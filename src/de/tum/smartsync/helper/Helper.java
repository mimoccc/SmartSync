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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Locale;
import java.util.Random;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.BatteryManager;
import android.os.PowerManager;
import de.tum.smartsync.Resource;
import de.tum.smartsync.background.statistics.StatisticProcessor;

/**
 * This class contains several constants and helper methods for various tasks.
 * 
 * @author Daniel
 * 
 */
public class Helper {

	// Global Log TAG
	public static final String TAG = "SmartSync";

	/**
	 * All ASCII alphanumerical characters (UPPER and lower case)
	 */
	public static final char[] CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890"
			.toCharArray();

	private static Random r;

	/**
	 * Creates a random string for random file names using a PRNG. Don't rely on
	 * this for any cryptographic operations.
	 * 
	 * @return A random string containing letters (capital and small) and
	 *         numbers. Its length is 10.
	 */
	public static String randomFileName() {
		if (r == null)
			r = new Random(TimeProvider.currentTimeMillis());
		StringBuilder sb = new StringBuilder(10);
		for (int i = 0; i < 10; i++)
			sb.append(CHARS[r.nextInt(CHARS.length)]);
		return sb.toString();
	}

	/**
	 * <p>
	 * Convert a number representing bytes into a easy to read format using SI
	 * prefixes (K, M, G, T, P) and one digit after the point (or equivalent
	 * decimal separator for the users current locale).
	 * 
	 * <p>
	 * Please mind that in this method one KB consists of exactly 1000 B (and
	 * not of 1024).
	 * 
	 * <p>
	 * Example outputs are:
	 * <ul>
	 * <li>456 B</li>
	 * <li>3.5 MB</li>
	 * <li>1999.9 TB</li>
	 * </ul>
	 * 
	 * @param bytes
	 *            The number of bytes to be converted
	 * @return A nicely formated string for user interfaces or debugging
	 */
	@SuppressLint("DefaultLocale")
	public static String nicelyFormatBytes(int bytes) {
		// constants
		final String[] SUFFIX = new String[] { "B", "KB", "MB", "GB", "TB",
				"PB" };
		final int STEP = 1000;
		final double LOG_STEP = Math.log(STEP);

		// do not use comma when only having bytes and no higher unit is
		// suitable
		if (bytes < STEP)
			return bytes + " B";

		// calc
		int howManySteps = (int) (Math.log(bytes) / LOG_STEP);
		String suffix = SUFFIX[howManySteps];
		double number = (double) bytes / Math.pow(STEP, howManySteps);

		// format (using user's preferred decimal separator) and return
		return String.format(Locale.getDefault(), "%.1f %s", number, suffix);
	}

	/**
	 * Returns a string describing the given day of week. Obeys
	 * <code>java.util.Calendar</code> and
	 * <code>StatisticProcessor.DAY_ANY</code>
	 * 
	 * @return One of the following: Sunday, Monday, Tuesday, Wednesday,
	 *         Thursday, Friday, Saturday, Anyday
	 */
	public static String getNameOfDay(int dayOfWeek) {
		if (dayOfWeek == StatisticProcessor.DAY_ANY)
			return "Anyday";

		if (dayOfWeek >= 1 && dayOfWeek <= 7) {
			Calendar cal = Calendar.getInstance(Locale.ENGLISH);
			cal.set(Calendar.DAY_OF_WEEK, dayOfWeek);
			return cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG,
					Locale.ENGLISH);
		}

		return "Unknown";
	}

	/**
	 * Convenient way to determine if the device is currently connected to an
	 * external power source.
	 * 
	 * @return True if the device is currently plugged in (either AC or USB).
	 */
	public static boolean isPowerConnected(Context context) {
		IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent battery = context.registerReceiver(null, ifilter);

		int chargePlug = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
		return (chargePlug == BatteryManager.BATTERY_PLUGGED_AC || chargePlug == BatteryManager.BATTERY_PLUGGED_USB);
	}

	/**
	 * Convenient way to determine if the device is currently facing a low or
	 * critical battery level
	 * 
	 * @return True if the battery level is quite low (<25%)
	 */
	public static boolean isPowerLow(Context context) {
		IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		Intent battery = context.registerReceiver(null, ifilter);

		int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

		return ((float) level / (float) scale) < 0.25;
	}

	/**
	 * This method drops all tables of a given database
	 * 
	 * @param db
	 *            Database of which we want to drop all tables
	 */
	public static void dropAllTables(SQLiteDatabase db) {
		// / drop all tables
		Cursor c = db.rawQuery(
				"SELECT name FROM sqlite_master WHERE type='table'", null);
		while (c.moveToNext()) {
			final String name = c.getString(c.getColumnIndex("name"));
			db.execSQL("DROP TABLE IF EXISTS " + name + ";");
			db.execSQL("DROP INDEX IF EXISTS uri_index_" + name + ";");

		}
		c.close();
	}

	/**
	 * Used to determine whether the user is currently using his/her device or
	 * not.
	 * 
	 * @return <code>true</code> if the display is on
	 */
	public static boolean isDisplayOn(Context context) {
		PowerManager powerManager = (PowerManager) context
				.getSystemService(Context.POWER_SERVICE);
		return powerManager.isScreenOn();
	}

	/**
	 * Allows to sort resources ascending by their priority.
	 */
	public static class SortResourceByPrioAsc implements Comparator<Resource> {

		@Override
		public int compare(Resource lhs, Resource rhs) {
			return lhs.getConfig().getPriority()
					- rhs.getConfig().getPriority();
		}

	}

	public static void writeArrayToStream(byte[] data, OutputStream out)
			throws IOException {
		out.write(data);
	}

	/**
	 * Reads a input stream into an array.
	 * 
	 * @param size
	 *            If the size is known this value can be used to read more
	 *            efficient. Otherwise set it to -1.
	 * @return
	 * @throws IOException
	 */
	public static byte[] readStreamIntoArray(InputStream in, int size)
			throws IOException {
		ByteArrayOutputStream bos;
		if (size > 0)
			bos = new ByteArrayOutputStream(size);
		else
			bos = new ByteArrayOutputStream();

		byte buf[] = new byte[4 * 1024];
		while (true) {
			int len = in.read(buf);

			// finished reading
			if (len == -1)
				break;

			// transport to buffer
			bos.write(buf, 0, len);
		}
		bos.flush();
		return bos.toByteArray();
	}

	public static final int FILE_SIZE_UNKOWN = -1;

	/**
	 * <p>
	 * Recursively removes all directories and files within this directory.
	 * Finally deletes the given directory.
	 * 
	 * <p>
	 * This method does nothing if <code>directory</code> is not a directory
	 */
	public static void clearDirectory(File directory) {
		if (!directory.isDirectory())
			return;

		final File files[] = directory.listFiles();
		for (File file : files) {
			if (file.isDirectory())
				clearDirectory(file);
			file.delete();
		}

	}
}
