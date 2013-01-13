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
package de.tum.smartsync.connectivity;

import de.tum.smartsync.Resource;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * This class is used internally as a reference for information of the current
 * connection type. It provides hints for speed and supposed delays between
 * requests.
 * 
 * @author Daniel
 * 
 */
public class ConnectionGuru {

	private static final String TAG = "ConnectionGuru";

	/**
	 * If detection failed.
	 */
	public static final int CONNECTION_UNKNOWN = 0x00;

	/**
	 * There is currently no active connection
	 */
	public static final int CONNECTION_NONE = 0x10;

	/**
	 * Includes: GPRS, EDGE
	 */
	public static final int CONNECTION_MOBILE_SLOW = 0x20;

	/**
	 * Includes: UMTS, HSDPA, LTE
	 */
	public static final int CONNECTION_MOBILE_FAST = 0x30;

	/**
	 * Includes: all kinds of WIFI
	 */
	public static final int CONNECTION_WIFI = 0x40;

	ConnectivityManager mConnectivityManager;
	TelephonyManager mTelephonyManager;

	public ConnectionGuru(Context context) {
		mConnectivityManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		mTelephonyManager = (TelephonyManager) context
				.getSystemService(Context.TELEPHONY_SERVICE);
	}

	/**
	 * Returns a non-localized despription of the connection type
	 */
	public static String getDescription(int connection) {
		switch (connection) {
		case CONNECTION_MOBILE_SLOW:
			return "mobile_slow";
		case CONNECTION_MOBILE_FAST:
			return "mobile_fast";
		case CONNECTION_WIFI:
			return "wifi";
		default:
			return "unkown";
		}
	}

	/**
	 * This methods provides one with hints how big the delays between accessing
	 * the network should be.
	 * 
	 * @param connection
	 *            The connection type for which one want to know the delay
	 *            factor
	 * @return A factor between 0.1 (less delay) and 10.0 (more delay)
	 */
	public static float getDelayFactor(int connection) {
		switch (connection) {
		case CONNECTION_NONE:
			return 1f;
		case CONNECTION_MOBILE_SLOW:
			return 10f;
		case CONNECTION_MOBILE_FAST:
			return 10f;
		case CONNECTION_WIFI:
			return 0.1f;
		default:
			return 1.0f;
		}
	}

	/**
	 * This methods provides one with hints how fast the given connection is.
	 * 
	 * @param connection
	 *            The connection type for which one want to know the speed
	 *            factor
	 * @return A factor between 0.1 (slower) and 10.0 (faster). A fast mobile
	 *         connection is regarded as 1.0.
	 */
	public static float getSpeedFactor(int connection) {
		switch (connection) {
		case CONNECTION_NONE:
			return 0f;
		case CONNECTION_MOBILE_SLOW:
			return 0.1f;
		case CONNECTION_MOBILE_FAST:
			return 1.0f;
		case CONNECTION_WIFI:
			return 10.0f;
		default:
			return 1.0f;
		}
	}

	/**
	 * Returns the current type of connection
	 */
	public int getCurrentConnection() {

		try {
			NetworkInfo activeNetwork = mConnectivityManager
					.getActiveNetworkInfo();

			// check whether there is no connection at the moment
			if (activeNetwork == null
					|| !activeNetwork.isConnectedOrConnecting())
				return CONNECTION_NONE;

			switch (activeNetwork.getType()) {

			case (ConnectivityManager.TYPE_WIFI):
				// WIFI
				return CONNECTION_WIFI;

			case (ConnectivityManager.TYPE_MOBILE):
				// MOBILE
				switch (mTelephonyManager.getNetworkType()) {
				case 0x13: // 0x13 = TelephonyManager.NETWORK_TYPE_LTE
				case TelephonyManager.NETWORK_TYPE_HSDPA:
				case TelephonyManager.NETWORK_TYPE_UMTS:
					return CONNECTION_MOBILE_FAST;

				case TelephonyManager.NETWORK_TYPE_EDGE:
				case TelephonyManager.NETWORK_TYPE_GPRS:
					return CONNECTION_MOBILE_SLOW;

				}

			}
		} catch (Exception e) {
			Log.w(TAG,
					"Unable to determine connection type due to "
							+ e.getMessage(), e);
		}
		return CONNECTION_UNKNOWN;
	}

	/**
	 * Returns the expected quality level for a given connection
	 * 
	 * @return See Resource.NOT_AVAILABLE and Resource.QUALITY_***
	 */
	public static int getExpectedQuality(int connection) {
		switch (connection) {
		case CONNECTION_NONE:
			return Resource.NOT_AVAILABLE;
		case CONNECTION_MOBILE_SLOW:
			return Resource.QUALITY_WORST;
		case CONNECTION_MOBILE_FAST:
			return Resource.QUALITY_GOOD;
		case CONNECTION_WIFI:
			return Resource.QUALITY_BEST;
		default:
			Log.e(TAG, "Unknown connection in getExpectedQuality: "
					+ connection);
			return Resource.QUALITY_FAIR;
		}
	}

	/**
	 * Converts a estimated connection type value (e.g. created by calculating
	 * the average) to the nearest understandable connection type.
	 * 
	 */
	public static int toNearestRealConnectionType(float f) {
		if (f > 2f)
			return CONNECTION_WIFI;
		if (f > 0.2)
			return CONNECTION_MOBILE_FAST;
		if (f > 0.02)
			return CONNECTION_MOBILE_SLOW;

		return CONNECTION_NONE;
	}
}
