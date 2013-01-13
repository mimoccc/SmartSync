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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import junit.framework.Assert;
import android.content.Context;
import android.util.Log;
import android.util.Pair;
import de.tum.smartsync.RawResource;
import de.tum.smartsync.Resource;
import de.tum.smartsync.ResourceConfig;
import de.tum.smartsync.helper.Helper;
import de.tum.smartsync.helper.TimeProvider;
import de.tum.smartsync.resource.RawBigResource;

/**
 * <p>
 * This cache provider is solely based on a file system scheme. Therefore it is
 * easily able to work on the external storage. Therefore it can be configured
 * to persist even after the application has been uninstalled.
 * 
 * <p>
 * As no content has to be moved through byte arrays it is mainly attractive for
 * maintaining a few BigRawResources (>10MB).
 * 
 * @author Daniel
 * 
 */
public class FileCacheProvider extends CacheProvider {

	private final static String TAG = "FileCacheProvider";

	private final static String REGEX_VALID_NAME = "\\w+";
	private final static String FILE_SUFFIX_INFO = ".info";
	private final static String FILE_SUFFIX_DATA = ".bin";

	private final static int LOCATION_EXTERNAL = 0x00;
	private final static int LOCATION_INTERNAL = 0x01;
	private final static int LOCATION_INTERNAL_CACHE = 0x10;
	private final static int LOCATION_EXTERNAL_CACHE = 0x11;

	private final static int LOCATION = LOCATION_INTERNAL_CACHE;

	private static final int BUFFER_SIZE = 32 * 1024;

	private File cacheDir;

	private String mName;

	public FileCacheProvider(String name, Context context) {
		// check name
		if (!name.matches(REGEX_VALID_NAME))
			throw new IllegalArgumentException(
					"Given name doesn't fulfil requirements.");
		this.mName = name;

		// locate directory for cache
		File cacheBaseDir = null;
		switch (LOCATION) {
		case LOCATION_EXTERNAL:
			cacheBaseDir = context.getExternalFilesDir(null);
			break;
		case LOCATION_EXTERNAL_CACHE:
			cacheBaseDir = context.getExternalCacheDir();
			break;
		case LOCATION_INTERNAL:
			cacheBaseDir = context.getFilesDir();
			break;
		case LOCATION_INTERNAL_CACHE:
			cacheBaseDir = context.getCacheDir();
			break;
		}
		cacheDir = new File(cacheBaseDir, mName);

		createCacheDir();
	}

	@Override
	public void close() {
		// nothing to do here
	}

	@Override
	public void clearCache() {
		// removes all files
		Helper.clearDirectory(cacheDir);
		createCacheDir();
	}

	private void createCacheDir() {
		// does already exist?
		if (cacheDir.exists() && cacheDir.isDirectory())
			return;

		// create folders
		boolean success = cacheDir.mkdir();
		if (!success)
			throw new RuntimeException(
					"Unable to create file cache directory: " + cacheDir);
		Log.d(TAG, "Created main cache directory: " + cacheDir);
	}

	@Override
	public void doExtensiveWork() {
		// todo: Clean up cache
		// especially remove dead content
		// perhaps also take care of not using too much space
	}

	@Override
	public void remove(Resource r) {
		List<Pair<File, File>> candidates = getFilesOfResource(r);

		for (Pair<File, File> p : candidates) {
			p.first.delete();
			p.second.delete();
		}
	}

	@Override
	public void cache(RawResource r) {
		File dir = getDirOfResource(r);

		// create directory if neccessary
		if (!dir.exists()) {
			boolean success = dir.mkdir();
			if (!success)
				throw new RuntimeException("Unable to create directory: " + dir);
			// Log.d(TAG, "Created directory: " + dir);
		}

		// check if outdated
		boolean shouldCache = shouldCache(r);
		if (!shouldCache)
			return;

		// get a name for the files; in the very rare case that the file exists,
		// it gets overwritten which is fine
		String fileName = Helper.randomFileName();
		File infoFile = new File(dir, fileName + FILE_SUFFIX_INFO);
		File dataFile = new File(dir, fileName + FILE_SUFFIX_DATA);

		try {
			// Log.d(TAG, "Writing to info file: " + infoFile);
			writeInfoFile(r, TimeProvider.currentTimeMillis(), infoFile);
		} catch (IOException e) {
			throw new RuntimeException("Writing info file failed!", e);
		}

		try {
			// Log.d(TAG, "Writing to data file: " + dataFile);
			writeDataFile(r, dataFile);
		} catch (IOException e) {
			// if this fails, we should also delete the info file
			boolean delSuccess = infoFile.delete();
			throw new RuntimeException(
					"Writing data file failed! Deletion of info file successful: "
							+ delSuccess, e);
		}
	}

	private void writeDataFile(RawResource r, File dataFile) throws IOException {
		OutputStream out = new BufferedOutputStream(new FileOutputStream(
				dataFile));

		if (r instanceof RawBigResource) {
			InputStream in = r.getNewInputStream();
			byte[] buf = new byte[BUFFER_SIZE];

			while (true) {
				int len = in.read(buf);

				// finished reading
				if (len == -1)
					break;

				// transport to buffer
				out.write(buf, 0, len);
			}

		} else {
			out.write(r.getFlatData());
		}

		out.flush();
		out.close();
	}

	private boolean shouldCache(Resource r) {
		List<Pair<File, File>> candidates = getFilesOfResource(r);
		final long NOW = TimeProvider.currentTimeMillis();

		// Iterate over all possible candidate files
		for (Pair<File, File> p : candidates) {
			File infoFile = p.first;
			Pair<RawResource, Long> info;
			try {
				info = parseInfoFile(infoFile);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			Resource rr = info.first;
			long timestamp = info.second;

			// is this entry already out dated but not yet removed?
			if (timestamp + rr.getConfig().getCacheLifespan() < NOW)
				continue;

			// is there any newer entry?
			if (timestamp > NOW)
				return false;

			// is there any of better quality?
			if (rr.getStatus() > r.getStatus())
				return false;
		}

		// otherwise: update!
		return true;
	}

	@Override
	public int getCachingStatus(Resource r) {
		List<Pair<File, File>> candidates = getFilesOfResource(r);
		final long NOW = TimeProvider.currentTimeMillis();

		int bestStatus = Resource.NOT_AVAILABLE;

		// Iterate over all possible candidate files
		for (Pair<File, File> p : candidates) {
			File infoFile = p.first;
			Pair<RawResource, Long> info;
			try {
				info = parseInfoFile(infoFile);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			Resource rr = info.first;
			long timestamp = info.second;
			boolean outdated = timestamp + rr.getConfig().getCacheLifespan() < NOW;

			if (outdated)
				continue;

			if (rr.getStatus() > bestStatus)
				bestStatus = rr.getStatus();
		}

		return bestStatus;
	}

	@Override
	public long getTimestamp(Resource r) {
		List<Pair<File, File>> candidates = getFilesOfResource(r);
		final long NOW = TimeProvider.currentTimeMillis();

		int bestStatus = Resource.NOT_AVAILABLE;
		long resultTimeStamp = 0;

		// Iterate over all possible candidate files
		for (Pair<File, File> p : candidates) {
			File infoFile = p.first;
			Pair<RawResource, Long> info;
			try {
				info = parseInfoFile(infoFile);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			Resource rr = info.first;
			long timestamp = info.second;
			boolean outdated = timestamp + rr.getConfig().getCacheLifespan() < NOW;

			if (outdated)
				continue;

			if (rr.getStatus() > bestStatus) {
				bestStatus = rr.getStatus();
				resultTimeStamp = timestamp;
			}

		}

		return resultTimeStamp;
	}

	@Override
	public void fillResource(RawResource r) {
		// determine best file
		List<Pair<File, File>> candidates = getFilesOfResource(r);
		final long NOW = TimeProvider.currentTimeMillis();

		int bestStatus = Resource.NOT_AVAILABLE;
		long newsestTimestamp = 0L;
		File bestContentFile = null;
		Resource bestResource = null;

		// Iterate over all possible candidate files
		for (Pair<File, File> p : candidates) {
			File infoFile = p.first;
			Pair<RawResource, Long> info;
			try {
				info = parseInfoFile(infoFile);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			Resource currentCandidate = info.first;
			long timestamp = info.second;

			// is this entry already out dated but not yet removed?
			if (timestamp + currentCandidate.getConfig().getCacheLifespan() < NOW)
				continue;

			// is there any newer entry?
			if (timestamp < newsestTimestamp)
				continue;

			// is there any of better quality?
			if (currentCandidate.getStatus() < bestStatus)
				continue;

			bestResource = currentCandidate;
			bestContentFile = p.second;
		}

		if (bestResource != null) {
			r.setConfig(bestResource.getConfig());
			r.setStatus(bestResource.getStatus());
			try {
				writeToResource(bestContentFile, r);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void writeToResource(File contentFile, RawResource r)
			throws IOException {
		if (r instanceof RawBigResource) {
			// it is a raw big resource
			RawBigResource rr = (RawBigResource) r;
			rr.replaceUnderlyingFile(contentFile);
		} else {
			// it is a normal raw resource
			InputStream in = new FileInputStream(contentFile);
			byte[] data = Helper.readStreamIntoArray(in,
					Helper.FILE_SIZE_UNKOWN);
			r.setData(data);
		}
	}

	@Override
	public int getCacheMethod() {
		return CacheProvider.METHOD_FILE;
	}

	@Override
	public boolean isOutdated(Resource r) {
		List<Pair<File, File>> candidates = getFilesOfResource(r);
		final long NOW = TimeProvider.currentTimeMillis();

		// Iterate over all possible candidate files
		for (Pair<File, File> p : candidates) {
			File infoFile = p.first;
			Pair<RawResource, Long> info;
			try {
				info = parseInfoFile(infoFile);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			// check if it is a valid entry
			Resource rr = info.first;
			long timestamp = info.second;
			boolean outdated = timestamp + rr.getConfig().getCacheLifespan() < NOW;

			if (outdated)
				continue;

			// there's at least one up-to-date version
			return true;
		}

		// there seem to be no up-to-date version
		return false;
	}

	@Override
	public String getCacheName() {
		return this.mName;
	}

	private List<Pair<File, File>> getFilesOfResource(Resource r) {
		List<Pair<File, File>> result = new LinkedList<Pair<File, File>>();

		File dir = getDirOfResource(r);
		if (!dir.exists() || !dir.isDirectory())
			return result;

		// dir does exists, so iterate through files
		File files[] = dir.listFiles();
		for (File infoFile : files) {
			// find info file
			final String infoFileName = infoFile.getName();
			if (!infoFileName.endsWith(FILE_SUFFIX_INFO))
				continue;
			// Log.v(TAG, "Check file: " + infoFile);

			// check if it has the wanted URI
			try {
				String uri = parseJustUriFromInfoFile(infoFile);
				if (!r.getPathUri().toString().equals(uri))
					continue;

			} catch (IOException e) {
				e.printStackTrace();
				continue;
			}

			// find corresponding content file
			String contentFileName = infoFileName.substring(0,
					infoFileName.length() - FILE_SUFFIX_INFO.length())
					+ FILE_SUFFIX_DATA;
			File contentFile = new File(dir, contentFileName);

			if (!contentFile.exists())
				continue;

			// add both to the result
			Pair<File, File> p = new Pair<File, File>(infoFile, contentFile);
			result.add(p);
		}

		return result;
	}

	private void writeInfoFile(Resource r, long timestamp, File f)
			throws IOException {
		OutputStream out = new BufferedOutputStream(new FileOutputStream(f));

		// WRITE MAGIC (R)
		out.write(new byte[] { (byte) 0xBA, (byte) 0xBE, (byte) 0xC0,
				(byte) 0xDE });

		// WRITE URI
		char[] chars = r.getPathUri().toString().toCharArray();
		int len = chars.length;
		ByteBuffer b1 = ByteBuffer.allocate(4 + 2 * len);
		b1.putInt(len);
		for (int i = 0; i < len; i++)
			b1.putChar(chars[i]);
		Assert.assertTrue(b1.remaining() == 0);
		out.write(b1.array());

		// WRITE STATUS AND TIMESTAMP
		ByteBuffer b2 = ByteBuffer.allocate(4 + 8);
		b2.putInt(r.getStatus());
		b2.putLong(timestamp);
		Assert.assertTrue(b2.remaining() == 0);
		out.write(b2.array());

		// WRITE RESOURCE CONFIG
		byte[] marshalled = r.getConfig().marshall();
		int len2 = marshalled.length;
		ByteBuffer b3 = ByteBuffer.allocate(4 + len2);
		b3.putInt(len2);
		b3.put(marshalled);
		Assert.assertTrue(b3.remaining() == 0);
		out.write(b3.array());

		// FLUSH
		out.flush();
		out.close();
	}

	/**
	 * Parses a given info file for meta information
	 * 
	 * @param f
	 *            The file to parse
	 * @return A pair consisting of the RawResource and the timestamp
	 * @throws IOException
	 */
	private Pair<RawResource, Long> parseInfoFile(File f) throws IOException {
		// The real IO operations are here
		InputStream in = new FileInputStream(f);
		byte array[] = Helper.readStreamIntoArray(in, Helper.FILE_SIZE_UNKOWN);
		in.close();

		ByteBuffer b = ByteBuffer.wrap(array);

		// CHECK MAGIC
		Assert.assertEquals((byte) 0xBA, b.get());
		Assert.assertEquals((byte) 0xBE, b.get());
		Assert.assertEquals((byte) 0xC0, b.get());
		Assert.assertEquals((byte) 0xDE, b.get());

		// READ URI
		int len = b.getInt();
		char chars[] = new char[len];
		for (int i = 0; i < len; i++)
			chars[i] = b.getChar();
		String uri = new String(chars);

		// READ STATUS AND TIMESTAMP AND EXPIRE
		int status = b.getInt();
		long timestamp = b.getLong();

		// READ CACHE CONFIG
		int len2 = b.getInt();
		byte[] cacheConfigRaw = new byte[len2];
		b.get(cacheConfigRaw);

		Assert.assertTrue(b.remaining() == 0);

		// BUILD OBJECTS
		ResourceConfig rc = new ResourceConfig(cacheConfigRaw);
		RawResource r = new RawResource(uri);
		r.setConfig(rc);
		r.setStatus(status);

		return new Pair<RawResource, Long>(r, timestamp);
	}

	private File getDirOfResource(Resource r) {
		// locate directory
		String hash = getHashForUri(r.getPathUri().toString());
		File dir = new File(cacheDir, hash);
		return dir;
	}

	public static String getHashForUri(String uri) {
		// absolute value to prevent minus sign
		// just reduces hash value range by half
		int i = Math.abs(uri.hashCode());
		return Integer.toString(i, 10 + 26);
	}

	private String parseJustUriFromInfoFile(File f) throws IOException {
		// The real IO operations are here
		InputStream in = new FileInputStream(f);
		byte array[] = Helper.readStreamIntoArray(in, Helper.FILE_SIZE_UNKOWN);
		in.close();

		ByteBuffer b = ByteBuffer.wrap(array);

		// CHECK MAGIC
		Assert.assertEquals((byte) 0xBA, b.get());
		Assert.assertEquals((byte) 0xBE, b.get());
		Assert.assertEquals((byte) 0xC0, b.get());
		Assert.assertEquals((byte) 0xDE, b.get());

		// READ URI
		int len = b.getInt();
		char chars[] = new char[len];
		for (int i = 0; i < len; i++)
			chars[i] = b.getChar();
		String uri = new String(chars);

		return uri;
	}

}
