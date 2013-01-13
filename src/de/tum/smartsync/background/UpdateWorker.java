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
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.util.Log;
import de.tum.smartsync.RawResource;
import de.tum.smartsync.Resource;
import de.tum.smartsync.caching.CacheProvider;
import de.tum.smartsync.connectivity.ConnectionGuru;
import de.tum.smartsync.connectivity.DoNotUpdateException;
import de.tum.smartsync.connectivity.ResourceProxy;

//You found it. This is where the magic happens. :)
/**
 * These workers are initiated by a concrete instance of the
 * {@link UpdateService} class. They are provided with all necessary
 * {@link CacheProvider}s and {@link ResourceProxy}s. Using a common list which
 * is synchronized via locks they independently download those resources.
 * 
 * @author Daniel
 * 
 */
public class UpdateWorker implements Runnable {

	public static final String HTTP_PARAM_CONNECTION = "c";

	public static final String HTTP_PARAM_EXPECTED_QUALITY = "q";

	public static final String HTTP_PARAM_TIMESTAMP = "ts";

	private String TAG = "UpdateWorker_";

	private static int index = 1;

	/** Synchronizes concurrent access on cache */
	private ReentrantLock lockCache;
	private CacheProvider cache;

	/** Synchronizes concurrent access on work queue */
	private ReentrantLock lockQueue;
	private List<RawResource> workQueue;

	private ResourceProxy mProxy;
	private ProgressListener listener;

	private Context context;

	final int myIndex;

	/**
	 * Initiates a new UpdateWorker thread.
	 * 
	 * @param context
	 *            Context for accessing the device
	 * @param lockCache
	 *            For synchronizing the cache accesses
	 * @param cache
	 *            The cache to use
	 * @param lockQueue
	 *            For synchronizing concurrent access on work queue
	 * @param workQueue
	 *            List with all resource to update
	 * @param proxyMethod
	 *            Used for generating own resource proxy
	 * @param proxyExtras
	 *            Used for generating own resource proxy
	 * @param listener
	 *            Used for various callbacks about current status
	 */
	public UpdateWorker(Context context, ReentrantLock lockCache,
			CacheProvider cache, ReentrantLock lockQueue,
			List<RawResource> workQueue, int proxyMethod, byte[] proxyExtras,
			ProgressListener listener) {
		super();
		this.lockCache = lockCache;
		this.cache = cache;
		this.lockQueue = lockQueue;
		this.workQueue = workQueue;
		this.listener = listener;
		this.context = context;

		// get new resource mProxy
		this.mProxy = ResourceProxy.getResourceProxy(context, proxyMethod,
				proxyExtras);
		this.mProxy.setProgressListener(listener);

		// not really safe, but safe enough
		myIndex = index++;
		this.TAG = this.TAG + myIndex;

		// Log.d(TAG, "created!");
	}

	@Override
	/**
	 * The actual update routine involving cache and resource proxy :)
	 */
	public void run() {
		Log.d(TAG, " started!");
		// [DEBUG]
		// TrafficStats.setThreadStatsTag(0xFF00 + myIndex);

		// check our current connectivity
		ConnectionGuru connGuru = new ConnectionGuru(context);
		final int conn = connGuru.getCurrentConnection();

		// if there is none: abort
		if (conn == ConnectionGuru.CONNECTION_NONE)
			throw new RuntimeException("No connection available!");

		final int expectedQuality = ConnectionGuru.getExpectedQuality(conn);

		// work until no resources are left for updating
		boolean running = true;
		while (running) {
			RawResource r = null;

			// PERFORMING CONCURRENT WORK ON WORKING QUEUE
			lockQueue.lock();
			try {
				// check if list is empty
				if (workQueue.isEmpty()) {
					running = false;
				} else {
					// else get new resources from list
					r = workQueue.get(0);
					workQueue.remove(0);
				}
			} finally {
				lockQueue.unlock();
			}
			if (!running)
				break;

			long timestamp;
			String uri = r.getPathUri().toString();
			Log.d(TAG, "Processing: " + uri);

			// PERFORMING CONCURRENT WORK ON CACHE
			lockCache.lock();
			try {
				// retrieve timestamp
				timestamp = cache.getTimestamp(r);

				// mark as currently updating
				r.setStatus(Resource.UPDATING);
				cache.cache(r);
			} finally {
				lockCache.unlock();
			}

			// prepare HTTP request
			List<NameValuePair> params = new LinkedList<NameValuePair>();
			params.add(new BasicNameValuePair(HTTP_PARAM_CONNECTION, Integer
					.toString(conn)));
			params.add(new BasicNameValuePair(HTTP_PARAM_EXPECTED_QUALITY,
					Integer.toString(expectedQuality)));
			params.add(new BasicNameValuePair(HTTP_PARAM_TIMESTAMP, Long
					.toString(timestamp)));

			// PERFORM ACTUAL HTTP ACCESS
			boolean updated = false;
			try {
				mProxy.doLoad(r, params);
				updated = true;
			} catch (MalformedURLException e) {
				listener.onError(uri, e.getLocalizedMessage());
				e.printStackTrace();
				continue;
			} catch (IOException e) {
				listener.onError(uri, e.getLocalizedMessage());
				e.printStackTrace();
				continue;
			} catch (DoNotUpdateException noUpdate) {
				Log.v(TAG, "Did not update resource (" + uri.toString()
						+ ") because: " + noUpdate.getMessage());
			}
			Log.d(TAG, "Loaded resource: " + uri);

			// if the proxy returned a new resource and did not thrown an
			// exception
			if (updated) {
				// set quality
				r.setStatus(expectedQuality);

				// PERFORMING CONCURRENT WORK ON CACHE
				lockCache.lock();
				try {
					// store it in cache
					cache.cache(r);
					Log.d(TAG, "Cached resource: " + uri);
				} finally {
					lockCache.unlock();
				}
			}

			// fire an UPDATE_RESOURCE
			listener.updatedResource(uri);

			// todo: remove sleep in background method?
			// but helped that following intents are correctly transferred by
			// the Android system. Somehow strange... needs further
			// investigation
			try {
				Thread.sleep(200);
			} catch (InterruptedException ignore) {
				// ignore, because not waiting doesn't harm
			}

		}

		// [DEBUG]
		// TrafficStats.clearThreadStatsTag();

		// Log.d(TAG, "finished!");

	}

}
