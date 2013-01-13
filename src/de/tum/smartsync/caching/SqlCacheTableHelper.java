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
package de.tum.smartsync.caching;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import de.tum.smartsync.background.DatabaseHelper;
import de.tum.smartsync.helper.Helper;

/**
 * Helper to access the database used by the SqlCacheProvider.
 * 
 * @author Daniel
 * 
 */
public class SqlCacheTableHelper extends SQLiteOpenHelper {

	public static final String DATABASE_NAME = "de.tum.smartsync.cachedb";

	private static final int DATABASE_VERSION = DatabaseHelper.DATABASE_VERSION + 3;

	public static final String KEY_ID = "i";
	public static final String KEY_URI = "u";
	public static final String KEY_STATUS = "s";
	public static final String KEY_TIMESTAMP = "t";
	public static final String KEY_DATA = "d";
	public static final String KEY_CONFIG = "c";
	public static final String KEY_EXPIRE = "e";
	public static final String KEY_PRIO = "p";

	private String mTableName;

	/**
	 * <p>
	 * Creates a new SqlCacheTableHelper.
	 * 
	 * @param context
	 * @param mTableName
	 *            The table to be used. This should be unique for the
	 *            application
	 */
	public SqlCacheTableHelper(Context context, String mTableName) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);

		this.mTableName = mTableName;

		SQLiteDatabase db = this.getWritableDatabase();
		String TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " + mTableName + " ("
				+ KEY_ID + " INTEGER PRIMARY KEY, " + KEY_URI + " TEXT, "
				+ KEY_STATUS + " INTEGER, " + KEY_TIMESTAMP + " INTEGER, "
				+ KEY_DATA + " BLOB, " + KEY_CONFIG + " BLOB, " + KEY_EXPIRE
				+ " INTEGER, " + KEY_PRIO + " INTEGER);";
		db.execSQL(TABLE_CREATE);
		db.execSQL("CREATE INDEX IF NOT EXISTS uri_index_" + mTableName
				+ " ON " + mTableName + "(" + KEY_URI + ");");
		db.close();
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		Helper.dropAllTables(db);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL("DROP TABLE IF EXISTS " + mTableName);
		db.execSQL("DROP INDEX IF EXISTS uri_index");
		onCreate(db);
	}

	public String getTableName() {
		return mTableName;
	}

}
