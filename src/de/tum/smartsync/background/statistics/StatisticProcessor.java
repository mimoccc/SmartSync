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
package de.tum.smartsync.background.statistics;

import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.util.Pair;
import de.tum.smartsync.connectivity.ConnectionGuru;
import de.tum.smartsync.helper.TimeProvider;

/**
 * <p>
 * This class regularly processes the raw statistic information in order to
 * create valuable results which are then cached.
 * 
 * <p>
 * In addition this class takes care that all raw statistic data is deleted
 * after 14 days.
 * 
 * @author Daniel
 * 
 */
public class StatisticProcessor {

	/**
	 * All statistical raw data before this point will be deleted
	 */
	private final static long DELETE_PERIODE = 14 * TimeProvider.DAY;

	/**
	 * All distilled information will be updated if they are older than this
	 * constant
	 */
	private final static long UPDATE_PERIODE = 6 * TimeProvider.HOUR;

	public final static int DAY_ANY = -1;

	private final static String TAG = "StatisticProcessor";

	Context mContext;

	public StatisticProcessor(Context context) {
		this.mContext = context;
	}

	/**
	 * <p>
	 * Returns whether the current statistical data are old enough, so that a
	 * new processing is clever. See <code>UPDATE_PERIODE</code> (1 day).
	 * 
	 * @return True if <code>process()</code> should be called.
	 */
	public boolean shouldProcess() {
		StatisticDbHelper statistic = new StatisticDbHelper(mContext);
		SQLiteDatabase db = statistic.getReadableDatabase();
		Cursor c = db.query(StatisticDbHelper.TABLE_NAME,
				new String[] { StatisticDbHelper.KEY_DATETIME }, null, null,
				null, null, StatisticDbHelper.KEY_DATETIME + " DESC");

		// no data? calculate some!
		if (c.isAfterLast()) {
			c.close();
			db.close();
			return true;
		}

		// is our data older than 6h?
		c.moveToFirst();
		long timestamp = c.getLong(0);
		c.close();
		db.close();
		if (timestamp < TimeProvider.currentTimeMillis() - UPDATE_PERIODE)
			return true;

		// if not: do nothing :)
		return false;
	}

	/**
	 * <p>
	 * Same as <code>forceProcess</code>. But checks and follows
	 * <code>shouldProcess</code> first.
	 */
	public void process() {
		if (shouldProcess())
			forceProcess();
	}

	// extracted constants
	private static final String[] columns1 = new String[] {
			Raw1DbHelper.KEY_CONNECTION, Raw1DbHelper.KEY_DISPLAY };
	private static final String[] columns2 = new String[] { Raw2DbHelper.KEY_APP_RUNNING };

	/**
	 * <p>
	 * Processes all collected background data and then also takes care to
	 * delete everything older than 7 days
	 */
	public void forceProcess() {
		Log.d(TAG, "Starting processing raw statistical data...");
		long startTime = TimeProvider.currentTimeMillis();

		Raw1DbHelper helper1 = new Raw1DbHelper(mContext);
		Raw2DbHelper helper2 = new Raw2DbHelper(mContext);
		StatisticDbHelper statistic = new StatisticDbHelper(mContext);

		// first delete all old distilled data
		statistic.deleteAllData();

		// collect data for each day
		SQLiteDatabase db1 = helper1.getReadableDatabase();
		SQLiteDatabase db2 = helper2.getReadableDatabase();
		SQLiteDatabase db = statistic.getReadableDatabase();

		// do it in transaction mode for more speed
		db.beginTransaction();

		// iterate over hours per day (24 hours)
		for (int hour = 0; hour <= 23; hour++) {
			// Log.v(TAG, "hour: " + hour);
			float globalSpeedSum = 0;
			float globalDisplaySum = 0;
			int globalActivityCalls = 0;

			// iterate from day 1 (usually Sunday) to day 7 (usually Saturday)
			for (int day = 1; day <= 7; day++) {

				// calculate average connection speed and display activity
				float speedSum = 0;
				float displaySum = 0;
				int cnt1 = 0;
				Cursor c1 = getRaw1ForTime(db1, day, hour, columns1);

				while (c1.moveToNext()) {
					speedSum += ConnectionGuru.getSpeedFactor(c1.getInt(0));
					displaySum += (float) c1.getInt(1);
					cnt1++;
					c1.moveToNext();
				}
				c1.close();
				float averageSpeed = cnt1 == 0 ? 1f : speedSum / (float) cnt1;
				float averageDisplay = cnt1 == 0 ? 0 : displaySum
						/ (float) cnt1;

				// count acitivty calls
				Cursor c2 = getRaw2ForTime(db2, day, hour, columns2);
				int activityCalls = c2.getCount();
				c2.close();

				// insert into statistic db
				statistic.insertData(db, day, hour, averageSpeed,
						averageDisplay, activityCalls);

				globalSpeedSum += averageSpeed;
				globalDisplaySum += averageDisplay;
				globalActivityCalls += activityCalls;
			}

			// insert for ANY_DAY day into statistic db
			statistic.insertData(db, DAY_ANY, hour, globalSpeedSum / 7f,
					globalDisplaySum / 7f, globalActivityCalls / 7);
		}

		// remove old data (E)
		helper1.deleteOldData(TimeProvider.currentTimeMillis() - DELETE_PERIODE);
		helper2.deleteOldData(TimeProvider.currentTimeMillis() - DELETE_PERIODE);

		// finish transaction
		db.setTransactionSuccessful();
		db.endTransaction();
		db.close();

		// be nice and close resources
		helper1.close();
		helper2.close();
		statistic.close();

		Log.d(TAG,
				"Finished processing after "
						+ (TimeProvider.currentTimeMillis() - startTime)
						+ " ms.");
	}

	private static final String WHERE2_DAY_AND_HOUR = Raw2DbHelper.KEY_DAY
			+ " = ? AND " + Raw2DbHelper.KEY_HOUR + " = ?";

	private static final String WHERE1_DAY_AND_HOUR = Raw1DbHelper.KEY_DAY
			+ " = ? AND " + Raw1DbHelper.KEY_HOUR + " = ?";

	private Cursor getRaw1ForTime(SQLiteDatabase db1, int day, int hour,
			String[] columns) {
		return db1.query(Raw1DbHelper.TABLE_NAME, columns, WHERE1_DAY_AND_HOUR,
				new String[] { "" + day, "" + hour }, null, null, null);
	}

	private Cursor getRaw2ForTime(SQLiteDatabase db2, int day, int hour,
			String[] columns) {
		return db2.query(Raw2DbHelper.TABLE_NAME, columns, WHERE2_DAY_AND_HOUR,
				new String[] { "" + day, "" + hour }, null, null, null);
	}

	/**
	 * <p>
	 * For debugging purposes only
	 */
	public void dumpToConsole() {
		StatisticDbHelper statistic = new StatisticDbHelper(mContext);
		statistic.dumpTableToConsole();
	}

	private final static String[] COLUMNS_DAY_HOUR_CONNECTION = new String[] {
			StatisticDbHelper.KEY_DAY, StatisticDbHelper.KEY_HOUR,
			StatisticDbHelper.KEY_CONNECTION_SPEED };

	private final static String[] COLUMNS_DAY_HOUR_USAGE = new String[] {
			StatisticDbHelper.KEY_DAY, StatisticDbHelper.KEY_HOUR,
			StatisticDbHelper.KEY_ACTIVITY };

	/**
	 * Provides the pre-processed statistical information about the average
	 * connection at a given time.
	 * 
	 * @return A map where the key has the format <code>< day, hour ></code> and
	 *         the value describes the average connection for that moment.
	 */
	public static Map<Pair<Integer, Integer>, Integer> getStatsConnection(
			Context context) {
		Map<Pair<Integer, Integer>, Integer> result = new HashMap<Pair<Integer, Integer>, Integer>();

		// connect to database
		StatisticDbHelper statistic = new StatisticDbHelper(context);
		SQLiteDatabase db = statistic.getReadableDatabase();

		// query, yeaha!
		Cursor c = db.query(StatisticDbHelper.TABLE_NAME,
				COLUMNS_DAY_HOUR_CONNECTION, null, null, null, null, null);

		// iterate over all aggregated data
		while (c.moveToNext()) {
			Pair<Integer, Integer> pii = new Pair<Integer, Integer>(
					c.getInt(0), c.getInt(1));
			result.put(pii, (int) c.getFloat(2));
		}

		c.close();
		db.close();

		return result;
	}

	/**
	 * Provides the pre-processed statistical information about the average
	 * usage at a given time.
	 * 
	 * @return A map where the key has the format <code>< day, hour ></code> and
	 *         the value describes the average usage for that moment.
	 */
	public static Map<Pair<Integer, Integer>, Integer> getStatsUsage(
			Context context) {
		Map<Pair<Integer, Integer>, Integer> result = new HashMap<Pair<Integer, Integer>, Integer>();

		// connect to database
		StatisticDbHelper statistic = new StatisticDbHelper(context);
		SQLiteDatabase db = statistic.getReadableDatabase();

		// query, yeaha!
		Cursor c = db.query(StatisticDbHelper.TABLE_NAME,
				COLUMNS_DAY_HOUR_USAGE, null, null, null, null, null);

		// iterate over all aggregated data
		while (c.moveToNext()) {
			Pair<Integer, Integer> pii = new Pair<Integer, Integer>(
					c.getInt(0), c.getInt(1));
			result.put(pii, c.getInt(2));
		}

		c.close();
		db.close();

		return result;
	}
}
