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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import de.tum.smartsync.connectivity.ConnectionGuru;
import de.tum.smartsync.helper.Helper;
import de.tum.smartsync.helper.TimeProvider;

/**
 * <p>
 * This service collects information on how the android device is used.
 * Collected information include: type of connectivity, powered on?
 * 
 * @author Daniel
 * 
 */
public class StatisticsService extends Service {

	public static final String TAG = "StatisticsService";

	/**
	 * <p>
	 * This method should be called by the activity every time it resumes.
	 */
	public static void initiateBackgroundService(Context context) {
		AlarmManager alarmManager = (AlarmManager) context
				.getSystemService(ALARM_SERVICE);

		Intent intent = new Intent(context, StatisticsService.class);
		PendingIntent pendingIntent = PendingIntent.getService(context, 0,
				intent, 0);

		alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP,
				TimeProvider.currentTimeMillis(),
				AlarmManager.INTERVAL_FIFTEEN_MINUTES, pendingIntent);

		// alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
		// TimeProvider.currentTimeMillis(), TimeProvider.SECOND * 10,
		// pendingIntent);
		Log.d(TAG, "Background service activated.");
	}

	public static void cancelBackgroundService(Context context) {
		AlarmManager alarmManager = (AlarmManager) context
				.getSystemService(ALARM_SERVICE);

		Intent intent = new Intent(context, StatisticsService.class);
		PendingIntent pendingIntent = PendingIntent.getService(context, 0,
				intent, 0);

		alarmManager.cancel(pendingIntent);
		Log.d(TAG, "Background service deactivated");
	}

	/**
	 * <p>
	 * Stores the information that the activity is currently in use.
	 */
	public static void markActivityInUse(Context context) {
		Raw2DbHelper helper = new Raw2DbHelper(context);
		helper.insertData(true);
		helper.close();
		Log.d(TAG, "Logged that the activity is in use.");
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		long start = TimeProvider.currentTimeMillis();

		gatherAndStoreInformation();

		long diff = TimeProvider.currentTimeMillis() - start;
		Log.d(TAG,
				"Stored information regarding connection, display and time in "
						+ diff + "ms.");

		// run stat processor regulary
		StatisticProcessor statProc = new StatisticProcessor(this);
		statProc.process();

	}

	private void gatherAndStoreInformation() {
		// get information
		ConnectionGuru cg = new ConnectionGuru(this);
		int conn = cg.getCurrentConnection();
		boolean display = Helper.isDisplayOn(this);

		// store them
		Raw1DbHelper helper = new Raw1DbHelper(this);
		helper.insertData(conn, display);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

}
