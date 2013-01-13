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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.util.Log;
import de.tum.smartsync.background.DatabaseHelper;
import de.tum.smartsync.helper.TimeProvider;

/**
 * <p>
 * Helper to access the database storing all information regarding collected
 * statistical data
 * 
 * <p>
 * This table stores information about activity activity associated with time
 * 
 * @author Daniel
 * 
 */
public class Raw2DbHelper extends SQLiteOpenHelper {

	private static final String DATABASE_NAME = "SmartSyncStat2";
	private static final int DATABASE_VERSION = DatabaseHelper.DATABASE_VERSION + 3;
	public static final String TABLE_NAME = "statistic_raw_2";

	public static final String KEY_ID = "id";
	public static final String KEY_DATETIME = "time";
	public static final String KEY_DAY = "day";
	public static final String KEY_HOUR = "hour";
	public static final String KEY_APP_RUNNING = "app";

	private static final String TABLE_CREATE = "CREATE TABLE " + TABLE_NAME
			+ " (" + KEY_ID + " INTEGER PRIMARY KEY, " + KEY_DATETIME
			+ " INTEGER, " + KEY_DAY + " INTEGER, " + KEY_HOUR + " INTEGER, "
			+ KEY_APP_RUNNING + " INTEGER);";

	public Raw2DbHelper(Context context) {
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

	/**
	 * <p>
	 * Adds a new row containing the given data
	 */
	public void insertData(boolean appRunning) {
		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put(KEY_DATETIME, TimeProvider.currentTimeMillis());
		values.put(KEY_DAY, TimeProvider.dayOfWeek());
		values.put(KEY_HOUR, TimeProvider.hourOfDay());
		values.put(KEY_APP_RUNNING, appRunning ? 1 : 0);

		db.insert(TABLE_NAME, null, values);
		db.close();
	}

	/**
	 * <p>
	 * Deletes every entry which is older than timestamp.
	 * 
	 * @param timestamp
	 *            Unix timestamp in milliseconds
	 */
	public void deleteOldData(long timestamp) {
		SQLiteDatabase db = this.getWritableDatabase();
		db.delete(TABLE_NAME, KEY_DATETIME + " < ?", new String[] { ""
				+ timestamp });
	}

	/**
	 * Backups the database to a file called "raw_stats.db" on your SD card.
	 * Consider this method ONLY FOR DEBUGGING!
	 * 
	 * @throws IOException
	 */
	public void backup() throws IOException {
		File sd = Environment.getExternalStorageDirectory();

		SQLiteDatabase db = this.getWritableDatabase();
		String SQLiteFileName = db.getPath();
		db.close();

		String backupFileName = "raw_stats2.db";

		File dbFile = new File(SQLiteFileName);
		File backupFile = new File(sd, backupFileName);

		Log.d("Raw1DbHelper", "from: " + dbFile.getCanonicalPath());
		Log.d("Raw1DbHelper", "to: " + backupFile.getCanonicalPath());

		FileChannel src = new FileInputStream(dbFile).getChannel();
		FileChannel dst = new FileOutputStream(backupFile).getChannel();
		dst.transferFrom(src, 0, src.size());
		Log.d("Raw1DbHelper", "copied: " + src.size());
		src.close();
		dst.close();
	}

	/**
	 * Restores the database from a file called "raw_stats.db" on your SD card.
	 * Consider this method ONLY FOR DEBUGGING!
	 * 
	 * @throws IOException
	 */
	public void restore() throws IOException {
		File sd = Environment.getExternalStorageDirectory();

		SQLiteDatabase db = this.getReadableDatabase();
		String SQLiteFileName = db.getPath();
		db.close();

		String backupFileName = "raw_stats2.db";

		File dbFile = new File(SQLiteFileName);
		File backupFile = new File(sd, backupFileName);

		FileChannel src = new FileInputStream(backupFile).getChannel();
		FileChannel dst = new FileOutputStream(dbFile).getChannel();
		dst.transferFrom(src, 0, src.size());
		src.close();
		dst.close();
	}

}
