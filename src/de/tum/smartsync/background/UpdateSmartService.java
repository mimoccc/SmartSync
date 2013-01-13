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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.Pair;
import de.tum.smartsync.RawResource;
import de.tum.smartsync.SyncIntent;
import de.tum.smartsync.background.statistics.StatisticProcessor;
import de.tum.smartsync.caching.CacheProvider;
import de.tum.smartsync.connectivity.ConnectionGuru;
import de.tum.smartsync.connectivity.ResourceProxy;
import de.tum.smartsync.helper.Helper;
import de.tum.smartsync.helper.TimeProvider;
import de.tum.smartsync.manager.ResourceManagerTableHelper;
import de.tum.smartsync.manager.SmartResourceManager;

/**
 * <p>
 * This service is responsible for performing the updates initiated by an
 * {@link SmartResourceManager}.
 * 
 * <p>
 * It takes care of loading the list of resources, connecting to statistical
 * data, running the smart update scheduler and then initiating the update of
 * the (selected) resources.
 * 
 * <p>
 * It is important that the Intent contains the name of the resource manager in
 * order to process the correct background table.
 * 
 * @author Daniel
 * 
 */
public class UpdateSmartService extends UpdateService {

	private final static int NUM_WORKERS = 2;

	static final String TAG = "UpdateSmartService";

	public UpdateSmartService() {
		super(TAG);
	}

	/**
	 * Called by the SmartResourceManager. Do not call this method directly from
	 * your activity.
	 */
	public static void activate(Context context, long updateInterval,
			String resourceManagerName, CacheProvider cache, ResourceProxy proxy) {
		// get alarm manager
		AlarmManager alarmManager = (AlarmManager) context
				.getSystemService(ALARM_SERVICE);
		// create intent
		Intent intent = new Intent(SyncIntent.UPDATE_NOW);
		intent.setClass(context, UpdateSmartService.class);

		intent.putExtra(SyncIntent.EXTRA_NAME, resourceManagerName);

		intent.putExtra(SyncIntent.EXTRA_CACHE_METHOD, cache.getCacheMethod());
		intent.putExtra(SyncIntent.EXTRA_CACHE_NAME, cache.getCacheName());

		intent.putExtra(SyncIntent.EXTRA_PROXY_METHOD, proxy.getProxyMethod());
		intent.putExtra(SyncIntent.EXTRA_PROXY_EXTRA, proxy.getProxyExtra());

		PendingIntent pendingIntent = PendingIntent.getService(context, 0,
				intent, 0);

		// alarmManager
		// .setInexactRepeating(AlarmManager.RTC_WAKEUP,
		// TimeProvider.currentTimeMillis(), updateInterval,
		// pendingIntent);

		alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
				TimeProvider.currentTimeMillis(), TimeProvider.SECOND * 10,
				pendingIntent);

		Log.d(TAG, "Background updates activated.");
	}

	/**
	 * Called by the SmartResourceManager. Do not call this method directly from
	 * your activity.
	 */
	public static void deactivate(Context context) {
		AlarmManager alarmManager = (AlarmManager) context
				.getSystemService(ALARM_SERVICE);

		Intent intent = new Intent(SyncIntent.UPDATE_NOW);
		intent.setClass(context, UpdateSmartService.class);

		PendingIntent pendingIntent = PendingIntent.getService(context, 0,
				intent, 0);

		alarmManager.cancel(pendingIntent);
		Log.d(TAG, "Background service deactivated");
	}

	@Override
	protected void internalUpdateResources(Context context)
			throws MalformedURLException, IOException {
		Log.d(TAG, "internalUpdateResources()");

		ConnectionGuru connGuru = new ConnectionGuru(context);
		final int conn = connGuru.getCurrentConnection();

		// create list
		List<RawResource> workQueue = ResourceManagerTableHelper
				.getAllResources(context, mRessourceManagerName, cacheMethod);

		// call decision algorithm and provide input data
		DecisionAlgorithm algo = new DecisionAlgorithm();

		final boolean isPowerConnected = Helper.isPowerConnected(context);
		final boolean isPowerLow = Helper.isPowerLow(context);
		final Map<Pair<Integer, Integer>, Integer> statsConnection = StatisticProcessor
				.getStatsConnection(this);
		final Map<Pair<Integer, Integer>, Integer> statsUsage = StatisticProcessor
				.getStatsUsage(this);

		if (statsConnection.size() < 24 * 7)
			throw new RuntimeException(
					"do not have sufficient data! (statsConnection)");
		if (statsUsage.size() < 24 * 7)
			throw new RuntimeException(
					"do not have sufficient data! (statsUsage)");

		algo.setInput(conn, workQueue, cache, isPowerConnected, isPowerLow,
				statsConnection, statsUsage);

		// run the algorithm for filtering our current resource list
		Log.d(TAG, "list size before algorithm: " + workQueue.size());
		algo.run(); // <-- there the magic happens :)
		workQueue = algo.getUpdateList();
		Log.d(TAG, "list size after algorithm: " + workQueue.size());

		ReentrantLock lockQueue = new ReentrantLock();
		ReentrantLock lockCache = new ReentrantLock();

		// create workers
		LinkedList<Thread> workers = new LinkedList<Thread>();
		for (int i = 0; i < NUM_WORKERS; i++) {
			UpdateWorker worker = new UpdateWorker(getApplicationContext(),
					lockCache, cache, lockQueue, workQueue, proxyMethod,
					proxyExtras, this);
			Thread t = new Thread(worker);
			workers.add(t);
		}

		// start workers
		for (Thread t : workers)
			t.start();

		// wait for them to finish
		for (Thread t : workers) {
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

}
