// SmartSync is an Android Framework for Smart Mobile Synchronization
// Copyright (C) 2013 Daniel Hugenroth
// 
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
package de.tum.smartsync.background;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.util.Log;
import android.util.Pair;
import de.tum.smartsync.RawResource;
import de.tum.smartsync.Resource;
import de.tum.smartsync.background.statistics.StatisticProcessor;
import de.tum.smartsync.caching.CacheProvider;
import de.tum.smartsync.connectivity.ConnectionGuru;
import de.tum.smartsync.helper.Helper;
import de.tum.smartsync.helper.TimeProvider;

/**
 * <p>
 * This class is used by the BackgroundService in order to determine which
 * resources should be updated.
 * 
 * <p>
 * The algorithm is excessively using the statistical background data generated
 * by the {@link StatisticProcessor}.
 * 
 * <p>
 * The normal usage of this algorithm is:
 * <ul>
 * <li>Calling constructor</li>
 * <li>Calling <code>setInput(...)</code></li>
 * <li>Calling <code>run()</code></li>
 * <li>Calling <code>getUpdateList()</code></li>
 * </ul>
 * 
 * @author Daniel
 * 
 */
public class DecisionAlgorithm {

	// general config for the heuristic (H)
	private static final int SCORE_START_VALUE = 200;
	private static final float SCORE_TRESHOLD = 1000f;

	// Used for configuring the rules for the heuristic
	private static final float RULE_POWER_LOW_PENALTY = -1000f;
	private static final float RULE_OUTDATED_BONUS = 1000f;
	private static final float RULE_RELATIVE_AGE_FACTOR = 150f;
	private static final float RULE_CONNECTION_FORECAST_FACTOR = 100f;
	private static final float RULE_USAGE_FACTOR = 80f;
	private static final float RULE_QUALITY_GAIN_FACTOR = 15f;
	private static final float RULE_CONNECTION_FACTOR = 20f;
	private static final float RULE_POWER_CONNECTED_PENALTY = -10f;
	private static final float RULE_POWER_CONNECTED_BONUS = 500f;

	private static final String TAG = "DecisionAlgorithm";

	private static final int DAY_ANY = StatisticProcessor.DAY_ANY;

	/** The current connection */
	private int connection = ConnectionGuru.CONNECTION_UNKNOWN;

	/** List of resources to be considered */
	private List<RawResource> resourceList = new LinkedList<RawResource>();

	/** The associated cache */
	private CacheProvider mCache;

	/** The final list of the resource to update */
	private LinkedList<RawResource> mUpdateList;

	/** Is the device currently connected to external power? */
	private boolean isPowerConnected;

	/** Is the power level currently low? */
	private boolean isPowerLow;

	/**
	 * Statistical background knowledge about connection quality at given time
	 */
	private Map<Pair<Integer, Integer>, Integer> statsConnection = new HashMap<Pair<Integer, Integer>, Integer>();

	/**
	 * Statistical background knowledge about application usage at given time
	 */
	private Map<Pair<Integer, Integer>, Integer> statsUsage = new HashMap<Pair<Integer, Integer>, Integer>();

	/**
	 * Creates a new DecisionAlgorithmOld. Before <code>run()</code> this object
	 * should initiated using the <code>setInput(...)</code> method.
	 */
	public DecisionAlgorithm() {

	}

	/**
	 * Initiate this instance given the specified context in terms of current
	 * environment measures and statistical background knowledge
	 * 
	 * @param connection
	 *            The current connection as from {@link ConnectionGuru}
	 * @param resourceList
	 *            List of resources to be considered
	 * @param mCache
	 *            The associated cache
	 * @param isPowerConnected
	 *            Is the device currently connected to external power?
	 * @param isPowerLow
	 *            Is the power level currently low?
	 * @param statsConnection
	 *            Statistical background knowledge about connection quality at
	 *            given time
	 * @param statsUsage
	 *            Statistical background knowledge about application usage at
	 *            given time
	 * @return
	 */
	public void setInput(int connection, List<RawResource> resourceList,
			CacheProvider mCache, boolean isPowerConnected, boolean isPowerLow,
			Map<Pair<Integer, Integer>, Integer> statsConnection,
			Map<Pair<Integer, Integer>, Integer> statsUsage) {
		this.connection = connection;
		this.resourceList = resourceList;
		this.mCache = mCache;
		this.isPowerConnected = isPowerConnected;
		this.isPowerLow = isPowerLow;
		this.statsConnection = statsConnection;
		this.statsUsage = statsUsage;
	}

	/**
	 * Runs the algorithm and creates the list of the resource needed to be
	 * updated
	 */
	public void run() {
		mUpdateList = new LinkedList<RawResource>();

		// Do a pre-check
		if (!precheck()) {
			Log.d(TAG, "Failed pre-check");
			return;
		}

		// check for each resource
		for (RawResource r : resourceList) {
			if (shouldUpdateResource(r))
				mUpdateList.add(r);
		}

		// sort list
		Collections.sort(mUpdateList, new Helper.SortResourceByPrioAsc());
	}

	/**
	 * Decide on our rule-based heuristic whether to update this resource or not
	 */
	private boolean shouldUpdateResource(Resource r) {
		float score = SCORE_START_VALUE;

		// int status = mCache.getCachingStatus(r);
		// Log.v(TAG, "Current caching status: " + status);

		// RULE: POWER CONNECTED
		score += rulePowerConnected();
		// Log.v(TAG, "  rulePowerConnected: " + rulePowerConnected());

		// RULE: CONNECTION
		score += ruleConnection();
		// Log.v(TAG, "  ruleConnection: " + ruleConnection());

		// RULE: OUTDATED?
		score += ruleOutdated(r);
		// Log.v(TAG, "  ruleOutdated: " + ruleOutdated(r));

		// RULE: RELATIVE AGE
		score += ruleRelativeAge(r);
		// Log.v(TAG, "  ruleRelativeAge: " + ruleRelativeAge(r));

		// RULE: BATTERY LOW
		score += rulePowerLow(r);
		// Log.v(TAG, "  rulePowerLow: " + rulePowerLow(r));

		// RULE: CONNECTION DEVELOPMENT
		score += ruleConnectionForecast();
		// Log.v(TAG, "  ruleConnectionForecast: " + ruleConnectionForecast());

		// RULE: USAGE NEXT HOUR
		score += ruleUsage();
		// Log.v(TAG, "  ruleUsageNextHour: " + ruleUsage());

		// RULE: QUALITY GAIN
		score += ruleQualityGain(r);
		// Log.v(TAG, "  ruleQualityGain: " + ruleQualityGain(r));

		Log.d(TAG, "Having a score of: " + score);

		return score >= SCORE_TRESHOLD;
	}

	/**
	 * Reward power connection
	 */
	private float rulePowerConnected() {
		if (isPowerConnected)
			return RULE_POWER_CONNECTED_BONUS;
		return RULE_POWER_CONNECTED_PENALTY;
	}

	/**
	 * Reward fast connection
	 */
	private float ruleConnection() {
		final float currentSpeed = ConnectionGuru.getSpeedFactor(connection);
		return currentSpeed * RULE_CONNECTION_FACTOR;
	}

	/**
	 * Reward a possible quality gain
	 */
	private float ruleQualityGain(Resource r) {
		final int currentQuality = mCache.getCachingStatus(r);
		final int expectedQuality = ConnectionGuru
				.getExpectedQuality(connection);
		final float qualityGain = expectedQuality - currentQuality;
		return Math.max(0f, qualityGain * RULE_QUALITY_GAIN_FACTOR);
	}

	/**
	 * Reward possible use in near future
	 */
	private float ruleUsage() {
		return (1 * averageCallsNextHour() + 2 * averageCallsThisHour())
				* RULE_USAGE_FACTOR;
	}

	/**
	 * Reward possible connection degradation; Punish future expected connection
	 * improvements
	 */
	private float ruleConnectionForecast() {
		final float currentSpeed = ConnectionGuru.getSpeedFactor(connection);
		final float futureSpeed = averageConnectionSpeedNextHour();
		// Log.d(TAG, "CurrentSpeed: " + currentSpeed + " and FutureSpeed: "
		// + futureSpeed);
		return (currentSpeed - futureSpeed) * RULE_CONNECTION_FORECAST_FACTOR;
	}

	/**
	 * Reward high-relative age of a resource
	 */
	private float ruleRelativeAge(Resource r) {
		// ignore rule when not cached
		if (mCache.getCachingStatus(r) <= Resource.NOT_AVAILABLE)
			return 0f;

		final long timestamp = mCache.getTimestamp(r)
				- r.getConfig().getCacheLifespan();
		final long age = TimeProvider.currentTimeMillis() - timestamp;
		// Log.d(TAG, "    timestamp: " + timestamp);
		// Log.d(TAG, "    currentTimeMillis: " +
		// TimeProvider.currentTimeMillis());
		// Log.d(TAG, "    age: " + age);
		final long updateInterval = r.getConfig().getUpdateInterval();
		final float relativeAge = (float) age / (float) updateInterval;
		return relativeAge * RULE_RELATIVE_AGE_FACTOR;
	}

	/**
	 * Highly reward if a resource is outdated
	 */
	private float ruleOutdated(Resource r) {
		final boolean isOutdated = mCache.isOutdated(r);
		if (isOutdated)
			return RULE_OUTDATED_BONUS;
		return 0f;
	}

	/**
	 * Punish a low power level
	 */
	private float rulePowerLow(Resource r) {
		if (isPowerLow)
			return RULE_POWER_LOW_PENALTY;
		return 0f;
	}

	/**
	 * @return <code>True</code> if we shall continue. <code>False</code> if we
	 *         shall not.
	 */
	private boolean precheck() {
		// no connection?
		if (connection == ConnectionGuru.CONNECTION_NONE) {
			Log.d(TAG, "PRE-CHECK fail: No connection");
			throw new RuntimeException("PRE-CHECK fail: No connection");
		}

		// is charging?
		if (isPowerConnected) {
			Log.d(TAG, "PRE-CHECK success: Power connected");
			return true;
		}

		// low power
		if (isPowerLow) {
			Log.d(TAG, "PRE-CHECK fail: Power low");
			throw new RuntimeException("PRE-CHECK fail: Power low");
		}

		return true;
	}

	/**
	 * The average number of calls within the next hour based on information
	 * from our statistical background knowledge.
	 */
	private float averageCallsNextHour() {
		// get current time
		int hour = TimeProvider.hourOfDay();
		int dayOfWeek = TimeProvider.dayOfWeek();
		//
		// get next hour (and responding day)
		++hour;
		if (hour > 23) {
			hour = 0;
			++dayOfWeek;
		}
		if (dayOfWeek > 6)
			dayOfWeek = 0;

		float average = 0;

		// query database for specific day (counted 3 times)
		average += 3 * statsUsage.get(new Pair<Integer, Integer>(dayOfWeek,
				hour));

		// query database for average of all days (counted 1 times)
		average += 1 * statsUsage
				.get(new Pair<Integer, Integer>(DAY_ANY, hour));

		return average / 4f;
	}

	/**
	 * The average number of calls within the next hour based on information
	 * from our statistical background knowledge.
	 */
	private float averageCallsThisHour() {
		// get current time
		int hour = TimeProvider.hourOfDay();
		int dayOfWeek = TimeProvider.dayOfWeek();

		float average = 0;

		// query database for specific day (counted 3 times)
		average += 3 * statsUsage.get(new Pair<Integer, Integer>(dayOfWeek,
				hour));

		// query database for average of all days (counted 1 times)
		average += 1 * statsUsage
				.get(new Pair<Integer, Integer>(DAY_ANY, hour));

		return average / 4f;
	}

	/**
	 * The average connection for the next hour based on information from our
	 * statistical background knowledge.
	 */
	private float averageConnectionSpeedNextHour() {
		// get current time
		int hour = TimeProvider.hourOfDay();
		int dayOfWeek = TimeProvider.dayOfWeek();
		//
		// get next hour (and responding day)
		++hour;
		if (hour > 23) {
			hour = 0;
			++dayOfWeek;
		}
		if (dayOfWeek > 6)
			dayOfWeek = 0;

		float average = 0;

		// query database for specific day (counted 3 times)
		average += 3 * statsConnection.get(new Pair<Integer, Integer>(
				dayOfWeek, hour));

		// query database for average of all days (counted 1 times)
		average += 1 * statsConnection.get(new Pair<Integer, Integer>(DAY_ANY,
				hour));

		int conn = ConnectionGuru.toNearestRealConnectionType(average);
		return ConnectionGuru.getSpeedFactor(conn);
	}

	/**
	 * List of the resources to be updated.
	 * 
	 * @return A ordered (by priority) list of resources which should be updated
	 */
	public LinkedList<RawResource> getUpdateList() {
		return this.mUpdateList;
	}
}
