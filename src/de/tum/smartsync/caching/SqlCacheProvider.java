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

import java.io.IOException;
import java.util.TreeSet;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import de.tum.smartsync.RawResource;
import de.tum.smartsync.Resource;
import de.tum.smartsync.ResourceConfig;
import de.tum.smartsync.helper.Helper;
import de.tum.smartsync.helper.TimeProvider;
import de.tum.smartsync.resource.RawBigResource;

/**
 * Provides a cache based on a SqLite database. One should keep in mind that the
 * SqLite.Cursor is not suitable for very large data.
 * 
 * @author Daniel
 * 
 */
public class SqlCacheProvider extends CacheProvider {

	private static final String TAG = "SqlCacheProvider";

	private final int STRATEGY_FIFO = 0x00;
	private final int STRATEGY_DEFAULT = STRATEGY_FIFO;

	protected int mStrategy = STRATEGY_DEFAULT;

	private String mName;

	private SqlCacheTableHelper mDbHelper;

	SQLiteDatabase db;

	/**
	 * <p>
	 * Creates a new SQL cache. The database name should be unique to your
	 * application and the resource manager.
	 * 
	 * <p>
	 * This cache might throw an error if you try to cache BigRawResources.
	 * Please consider a more suitable CacheProvider for those big resources.
	 * 
	 * @param name
	 *            Used to reference this specific cache. You should choose a
	 *            consistent and unique name.
	 */
	public SqlCacheProvider(String name, Context context) {
		this.mDbHelper = new SqlCacheTableHelper(context, name);
		this.mName = name;
		this.db = mDbHelper.getWritableDatabase();
	}

	@Override
	public void clearCache() {
		db.delete(mName, null, null);
	}

	private static final String WHERE_URI = SqlCacheTableHelper.KEY_URI + "= ?";

	@Override
	public void remove(Resource r) {
		internalRemove(r, db);
	}

	private void internalRemove(Resource r, SQLiteDatabase db) {
		db.delete(mName, WHERE_URI, new String[] { r.getPathUri().toString() });
	}

	@Override
	public void cache(RawResource r) {
		final ResourceConfig config = r.getConfig();

		// determine if we should cache this object
		if (!shouldCache(db, r))
			return;

		// prepare ContentValues
		final byte[] dataConfig = config.marshall();

		ContentValues values = new ContentValues();
		values.put(SqlCacheTableHelper.KEY_URI, r.getPathUri().toString());
		values.put(SqlCacheTableHelper.KEY_STATUS, r.getStatus());
		values.put(SqlCacheTableHelper.KEY_TIMESTAMP,
				TimeProvider.currentTimeMillis());

		values.put(SqlCacheTableHelper.KEY_CONFIG, dataConfig);

		final long expireTime = TimeProvider.currentTimeMillis()
				+ config.getCacheLifespan();
		values.put(SqlCacheTableHelper.KEY_EXPIRE, expireTime);
		values.put(SqlCacheTableHelper.KEY_PRIO, config.getPriority());

		// insert content
		byte[] data = new byte[0];

		if (r instanceof RawBigResource) {
			// it is a RawBigResource
			RawBigResource rbr = (RawBigResource) r;
			try {
				// Log.v(TAG, "I am handling a RawBigResource!");
				data = Helper.readStreamIntoArray(r.getNewInputStream(),
						(int) rbr.getSize());
			} catch (IOException e) {
				Log.e(TAG, "Error while reading from a RawBigResource");
				e.printStackTrace();
			}
		} else {
			// it is a 'normal' RawResource
			data = r.getFlatData();
		}
		values.put(SqlCacheTableHelper.KEY_DATA, data);

		// insert
		db.insert(mName, null, values);
	}

	private static final String[] COLUMNS_STATUS_TIMESTAMP_EXPIRE = new String[] {
			SqlCacheTableHelper.KEY_STATUS, SqlCacheTableHelper.KEY_TIMESTAMP,
			SqlCacheTableHelper.KEY_EXPIRE };

	private boolean shouldCache(SQLiteDatabase db, RawResource r) {
		// get all interesting columns (status and time stamp) and rows (where
		// URI)
		Cursor c = db.query(mName, COLUMNS_STATUS_TIMESTAMP_EXPIRE, WHERE_URI,
				new String[] { r.getPathUri().toString() }, null, null, null);

		// no entries yet? we should cache it!
		if (c.getCount() == 0)
			return true;

		final long NOW = TimeProvider.currentTimeMillis();

		while (c.moveToNext()) {
			// is this entry already out dated but not yet removed?
			if (c.getLong(2) < NOW)
				continue;

			// is there any newer entry?
			if (c.getLong(1) > NOW)
				return false;

			// is there any of better quality?
			if (c.getInt(0) > r.getStatus())
				return false;
		}
		c.close();

		return true;
	}

	private static final String[] COLUMNS_STATUS_EXPIRE = new String[] {
			SqlCacheTableHelper.KEY_STATUS, SqlCacheTableHelper.KEY_EXPIRE };

	@Override
	public int getCachingStatus(Resource r) {
		Cursor c = db.query(mName, COLUMNS_STATUS_EXPIRE, WHERE_URI,
				new String[] { r.getPathUri().toString() }, null, null, null);

		int bestStatus = Resource.NOT_AVAILABLE;

		while (c.moveToNext()) {
			// out dated?
			if (c.getLong(1) < TimeProvider.currentTimeMillis())
				continue;

			if (c.getInt(0) > bestStatus)
				bestStatus = c.getInt(0);
		}

		c.close();

		return bestStatus;
	}

	private static final String[] COLUMNS_STATUS_TIMESTAMP_CONFIG_EXPIRE_ID = new String[] {
			SqlCacheTableHelper.KEY_STATUS, SqlCacheTableHelper.KEY_TIMESTAMP,
			SqlCacheTableHelper.KEY_CONFIG, SqlCacheTableHelper.KEY_EXPIRE,
			SqlCacheTableHelper.KEY_ID };

	private static final String[] COLUMNS_STATUS_TIMESTAMP_DATA_CONFIG_EXPIRE = new String[] {
			SqlCacheTableHelper.KEY_STATUS, SqlCacheTableHelper.KEY_TIMESTAMP,
			SqlCacheTableHelper.KEY_DATA, SqlCacheTableHelper.KEY_CONFIG,
			SqlCacheTableHelper.KEY_EXPIRE };

	private static final String WHERE_ID = SqlCacheTableHelper.KEY_ID + "= ?";

	@Override
	public void fillResource(RawResource r) {
		// query this resource from our DB
		Cursor c = db.query(mName, COLUMNS_STATUS_TIMESTAMP_CONFIG_EXPIRE_ID,
				WHERE_URI, new String[] { r.getPathUri().toString() }, null,
				null, null);

		long bestTimestamp = 0L;
		int bestStatus = r.getStatus();
		int bestIndex = -1;
		int bestId = -1;

		while (c.moveToNext()) {
			// is this entry already out dated but not yet removed?
			if (c.getLong(3) < TimeProvider.currentTimeMillis())
				continue;

			// is this entry's status worse than our current one?
			if (c.getInt(0) < bestStatus)
				continue;
			bestStatus = c.getInt(0);

			// had there been an entry with a newer time stamp?
			if (c.getLong(1) < bestTimestamp)
				continue;
			bestTimestamp = c.getLong(1);

			// remember position
			bestIndex = c.getPosition();
			bestId = c.getInt(4);
		}
		c.close();

		// nothing found?
		if (bestIndex == -1)
			return;

		// query data for best resource
		Cursor c2 = db.query(mName,
				COLUMNS_STATUS_TIMESTAMP_DATA_CONFIG_EXPIRE, WHERE_ID,
				new String[] { Integer.toString(bestId) }, null, null, null);
		c2.moveToNext();

		// fill resource
		r.setStatus(c2.getInt(0));

		if (r instanceof RawBigResource) {
			// it is a raw big resource
			RawBigResource rbr = (RawBigResource) r;
			rbr.resetContent();

			try {
				final byte data[] = c2.getBlob(2);
				if (data != null)
					Helper.writeArrayToStream(data, rbr.getOutputStream());
			} catch (IOException e) {
				throw new RuntimeException(
						"Error while writing cache content into RawBigResource",
						e);
			}
		} else {
			// it is just a normal RawResource
			final byte data[] = c2.getBlob(2);
			if (data != null)
				r.setData(data);
		}

		ResourceConfig config = r.getConfig();
		config.unmarshall(c2.getBlob(3));
	}

	@Override
	public void close() {
		if (db != null)
			db.close();
	}

	private static final String[] COLUMNS_STATUS_TIMESTAMP_ID = new String[] {
			SqlCacheTableHelper.KEY_STATUS, SqlCacheTableHelper.KEY_TIMESTAMP,
			SqlCacheTableHelper.KEY_ID };

	@Override
	public void doExtensiveWork() {
		db.beginTransaction();
		int cnt = 0;
		Log.d(TAG, "Cleaning my table");

		// delete all expired entries
		cnt += db.delete(mName, SqlCacheTableHelper.KEY_EXPIRE + " < ?",
				new String[] { "" + TimeProvider.currentTimeMillis() });

		// collect all entries HAVING count(*) > 1
		TreeSet<String> set = new TreeSet<String>();
		Cursor c1 = db.query(mName,
				new String[] { SqlCacheTableHelper.KEY_URI }, null, null,
				SqlCacheTableHelper.KEY_URI, "COUNT(*) > 1", null);
		while (c1.moveToNext())
			set.add(c1.getString(0));
		c1.close();

		// delete all unneeded entries of those

		/*
		 * we will keep the one which provides the best quality. if there are
		 * more than one, we will keep the one with the most current time stamp.
		 */
		for (String uri : set) {
			Cursor c2 = db.query(mName, COLUMNS_STATUS_TIMESTAMP_ID, WHERE_URI,
					new String[] { uri }, null, null, null);

			// determine best quality and timestamp
			int bestStatus = Resource.NOT_AVAILABLE;
			long bestTimestamp = 0L;
			int bestIndex = -1;

			while (c2.moveToNext()) {
				// is this entry's status worse than our current one?
				if (c2.getInt(0) < bestStatus)
					continue;
				bestStatus = c2.getInt(0);

				// had there been an entry with a newer time stamp?
				if (c2.getLong(1) < bestTimestamp)
					continue;
				bestTimestamp = c2.getLong(1);

				// remember position
				bestIndex = c2.getInt(2);
			}
			c2.close();

			// delete all except the best
			if (bestIndex >= 0) {
				cnt += db.delete(mName, SqlCacheTableHelper.KEY_ID + " <> ?",
						new String[] { Integer.toString(bestIndex) });
			}
		}

		db.setTransactionSuccessful();
		db.endTransaction();

		Log.d(TAG, "Deleted " + cnt + " entries.");

	}

	@Override
	public int getCacheMethod() {
		return CacheProvider.METHOD_SQL;
	}

	@Override
	public String getCacheName() {
		return this.mName;
	}

	private static final String[] COLUMNS_TIMESTAMP_CONFIG = new String[] {
			SqlCacheTableHelper.KEY_TIMESTAMP, SqlCacheTableHelper.KEY_CONFIG };

	@Override
	public boolean isOutdated(Resource r) {
		Cursor c = db.query(mName, COLUMNS_TIMESTAMP_CONFIG, WHERE_URI,
				new String[] { r.getPathUri().toString() }, null, null, null);

		boolean result = true;

		while (c.moveToNext()) {
			ResourceConfig conf = new ResourceConfig(c.getBlob(1));

			final long NOW = TimeProvider.currentTimeMillis();
			final long OUTDATE = c.getLong(0) + conf.getUpdateInterval();

			// there is at least one resource which is not out-dated
			if (NOW < OUTDATE)
				result = false;
		}
		c.close();

		return result;
	}

	private static final String[] COLUMNS_STATUS_EXPIRE_TIMESTAMP = new String[] {
			SqlCacheTableHelper.KEY_STATUS, SqlCacheTableHelper.KEY_EXPIRE,
			SqlCacheTableHelper.KEY_TIMESTAMP };

	@Override
	public long getTimestamp(Resource r) {
		Cursor c = db.query(mName, COLUMNS_STATUS_EXPIRE_TIMESTAMP, WHERE_URI,
				new String[] { r.getPathUri().toString() }, null, null, null);

		int bestStatus = Resource.NOT_AVAILABLE;
		long timeStamp = 0;

		while (c.moveToNext()) {
			// out dated?
			if (c.getLong(1) < TimeProvider.currentTimeMillis())
				continue;

			if (c.getInt(0) >= bestStatus) {
				bestStatus = c.getInt(0);
				if (c.getLong(2) >= timeStamp)
					timeStamp = c.getLong(2);
			}
		}

		c.close();
		return timeStamp;
	}

}
