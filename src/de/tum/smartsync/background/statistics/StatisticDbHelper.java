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

import java.util.Locale;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import de.tum.smartsync.background.DatabaseHelper;
import de.tum.smartsync.helper.TimeProvider;

/**
 * <p>
 * This database/table stores the distilled information generated by the
 * statistic processor.
 * 
 * @author Daniel
 * 
 */
public class StatisticDbHelper extends SQLiteOpenHelper {

	// different to DB name of RawResult* tables to prevent ourselves from
	// deadlocks
	private static final String DATABASE_NAME = "SmartSyncResults";

	private static final int DATABASE_VERSION = DatabaseHelper.DATABASE_VERSION;
	public static final String TABLE_NAME = "statistic_result";

	public static final String KEY_ID = "id";
	public static final String KEY_DATETIME = "time";
	public static final String KEY_DAY = "day";
	public static final String KEY_HOUR = "hour";
	public static final String KEY_CONNECTION_SPEED = "conn_speed";
	public static final String KEY_DISPLAY = "display";
	public static final String KEY_ACTIVITY = "ac";

	private static final String TABLE_CREATE = "CREATE TABLE " + TABLE_NAME
			+ " (" + KEY_ID + " INTEGER PRIMARY KEY, " + KEY_DATETIME
			+ " INTEGER, " + KEY_DAY + " INTEGER, " + KEY_HOUR + " INTEGER, "
			+ KEY_CONNECTION_SPEED + " REAL, " + KEY_DISPLAY + " REAL, "
			+ KEY_ACTIVITY + " INTEGER);";

	public StatisticDbHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(TABLE_CREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
		onCreate(db);
	}

	private static String[] COLUMNS_ACTIVITY = new String[] { KEY_ACTIVITY };
	private static String WHERE_DAY_HOUR = KEY_DAY + "=? AND " + KEY_HOUR
			+ "=?";

	/**
	 * Returns the average number of activity calls for a given day and hour
	 * 
	 * @param day
	 *            day of the week (1..7). Using -1 will return average over all
	 *            days
	 * @param hour
	 *            hour of the day (0..23)
	 * @return The number of average activity calls OR -1.0 if no such data
	 *         exists
	 */
	public double getAverageActivity(SQLiteDatabase db, int day, int hour) {
		Cursor c = db.query(TABLE_NAME, COLUMNS_ACTIVITY, WHERE_DAY_HOUR,
				new String[] { "" + day, "" + hour }, null, null, null);

		if (c.moveToFirst()) {
			return c.getDouble(0);
		} else {
			return -1.0;
		}
	}

	/**
	 * <p>
	 * Adds a new row containing the given data
	 */
	public void insertData(SQLiteDatabase db, int day, int hour,
			float connSpeed, float display, int activityCalls) {

		ContentValues values = new ContentValues();
		values.put(KEY_DATETIME, TimeProvider.currentTimeMillis());
		values.put(KEY_DAY, day);
		values.put(KEY_HOUR, hour);
		values.put(KEY_CONNECTION_SPEED, connSpeed);
		values.put(KEY_DISPLAY, display);
		values.put(KEY_ACTIVITY, activityCalls);

		db.insert(TABLE_NAME, null, values);
	}

	/**
	 * <p>
	 * Deletes every entry
	 */
	public void deleteAllData() {
		SQLiteDatabase db = this.getWritableDatabase();
		db.delete(TABLE_NAME, null, null);
		db.close();
	}

	/**
	 * <p>
	 * For debugging purposes only
	 * 
	 * <p>
	 * TODO remove in final APK
	 */
	public void dumpTableToConsole() {
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor c = db.query(TABLE_NAME, new String[] { KEY_ID, KEY_DATETIME,
				KEY_DAY, KEY_HOUR, KEY_CONNECTION_SPEED, KEY_DISPLAY,
				KEY_ACTIVITY }, null, null, null, null, null);

		while (c.moveToNext()) {
			String s = String.format(Locale.ENGLISH,
					"%3d %10d   %2d %2d   %2.3f %2.3f %3d", c.getInt(0),
					c.getLong(1), c.getInt(2), c.getInt(3), c.getFloat(4),
					c.getFloat(5), c.getInt(6));
			Log.v("TABLE_DUMP", s);
		}
		c.close();
		db.close();
	}
}