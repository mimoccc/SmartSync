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

import android.content.Context;
import android.util.Log;
import de.tum.smartsync.RawResource;
import de.tum.smartsync.manager.ResourceManager;
import de.tum.smartsync.manager.ResourceManagerTableHelper;

/**
 * <p>
 * This service is responsible for performing the updates initiated by an
 * resource manager. This service will be initiated by an UPDATE_NOW Intent sent
 * by either the resource manager itself or another background service which
 * acts in behalf of the {@link ResourceManager}.
 * 
 * <p>
 * It is important that the Intent contains the name of the resource manager in
 * order to process the correct background table.
 * 
 * @author Daniel
 * 
 */
public class UpdateNowService extends UpdateService {

	private final static int NUM_WORKERS = 3;

	static final String TAG = "UpdateNowService";

	public UpdateNowService() {
		super(TAG);
	}

	@Override
	protected void internalUpdateResources(Context context)
			throws MalformedURLException, IOException {
		Log.d(TAG, "internalUpdateResources()");

		// create list
		List<RawResource> workQueue = ResourceManagerTableHelper
				.getAllResources(context, mRessourceManagerName, cacheMethod);

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
