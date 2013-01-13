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
package de.tum.smartsync.helper;

import java.util.Calendar;

/**
 * <p>
 * This class does provide the CacheProviders and the DecisionAlgorithmOld with
 * the current time informations.
 * 
 * <p>
 * This encapsulating is necessary and useful: The Android platform does not
 * allow us to set the current time programmatically and we need modified times
 * to run simulations without completely changing the DecisionAlgoritm and
 * CacheProviders.
 * 
 * <p>
 * Also TimeProvider offers some convenient methods for determing the current
 * day of the week of hour of the day.
 * 
 * @author Daniel
 * 
 */
public class TimeProvider {

	// Time constants
	/** One second are 1000 milliseconds */
	public static final long SECOND = 1000L;

	/** One minute are 60000 milliseconds */
	public static final long MINUTE = 60L * SECOND;

	/** One hour are 3600000 milliseconds */
	public static final long HOUR = 60L * MINUTE;

	/** One day are 86400000 milliseconds */
	public static final long DAY = 24L * HOUR;

	/**
	 * This variable can be set to override the time provided by this Helper
	 * class.
	 */
	private static long customTime = -1;

	private static Calendar cal;

	public static long currentTimeMillis() {
		if (customTime >= 0)
			return customTime;
		else
			return System.currentTimeMillis();
	}

	public static long getCustomTime() {
		return customTime;
	}

	/**
	 * @return A value ranging from 1=Sunday to 7=Saturday
	 */
	public static int dayOfWeek() {
		return getCalendar().get(Calendar.DAY_OF_WEEK);
	}

	/**
	 * @return A value ranging from 00 to 23
	 */
	public static int hourOfDay() {
		return getCalendar().get(Calendar.HOUR_OF_DAY);
	}

	private static Calendar getCalendar() {
		if (cal == null) {
			// old calendar is not correct -> get a new one (A)
			cal = Calendar.getInstance();
			cal.setTimeInMillis(currentTimeMillis());
		}
		return cal;
	}

	/**
	 * Returns to the actual current time.
	 */
	public static void deactivateCustomTime() {
		TimeProvider.customTime = -1;
		// kill old calendar
		cal = null;
	}

	/**
	 * Any value >= 0 will force the SmartSync system to adapt to the new time.
	 * Use <code> deactivateCustomTime</code> in order to return to the default
	 * behaviour.
	 */
	public static void setCustomTime(long customTime) {
		TimeProvider.customTime = customTime;
		// kill old calendar
		cal = null;
	}

}
