/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2014 Philipp C. Heckel <philipp.heckel@gmail.com> 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.syncany.operations.init;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.syncany.config.Config;
import org.syncany.config.UserConfig;
import org.syncany.config.to.ConfigTO.ConnectionTO;
import org.syncany.config.to.DaemonConfigTO;
import org.syncany.config.to.FolderTO;
import org.syncany.config.to.RepoTO;
import org.syncany.crypto.CipherException;
import org.syncany.crypto.CipherSpec;
import org.syncany.crypto.CipherUtil;
import org.syncany.crypto.SaltedSecretKey;
import org.syncany.operations.Operation;
import org.syncany.plugins.UserInteractionListener;
import org.syncany.util.Base58;
import org.syncany.util.EnvironmentUtil;
import org.syncany.util.FileUtil;

/**
 * The abstract init operation implements common functions of the {@link InitOperation}
 * and the {@link ConnectOperation}. Its sole purpose is to avoid duplicate code in these
 * similar operations.
 *   
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public abstract class AbstractInitOperation extends Operation {
    protected static final Logger logger = Logger.getLogger(AbstractInitOperation.class.getSimpleName());
    
    protected UserInteractionListener listener;

	public AbstractInitOperation(Config config, UserInteractionListener listener) {
		super(config);
		this.listener = listener;
	}
	
	protected File createAppDirs(File localDir) throws IOException {
		if (localDir == null) {
			throw new RuntimeException("Unable to create app dir, local dir is null.");
		}

		File appDir = new File(localDir, Config.DIR_APPLICATION);
		File logDir = new File(appDir, Config.DIR_LOG);
		File cacheDir = new File(appDir, Config.DIR_CACHE);
		File databaseDir = new File(appDir, Config.DIR_DATABASE);
		File stateDir = new File(appDir, Config.DIR_STATE);

		appDir.mkdir();
		logDir.mkdir();
		cacheDir.mkdir();
		databaseDir.mkdir();
		stateDir.mkdir();

		if (EnvironmentUtil.isWindows()) {
			Files.setAttribute(Paths.get(appDir.getAbsolutePath()), "dos:hidden", true);
		}

		return appDir;
	}

	protected void deleteAppDirs(File localDir) throws IOException {
		File appDir = new File(localDir, Config.DIR_APPLICATION);
		File logDir = new File(appDir, Config.DIR_LOG);
		File cacheDir = new File(appDir, Config.DIR_CACHE);
		File databaseDir = new File(appDir, Config.DIR_DATABASE);

		for (File log : logDir.listFiles()) {
			log.delete();
		}

		for (File cache : cacheDir.listFiles()) {
			cache.delete();
		}

		for (File db : databaseDir.listFiles()) {
			db.delete();
		}

		for (File file : appDir.listFiles()) {
			file.delete();
		}

		logDir.delete();
		cacheDir.delete();
		databaseDir.delete();
		appDir.delete();
	}

	protected void writeXmlFile(Object source, File file) throws IOException {
		try {
			Serializer serializer = new Persister();
			serializer.write(source, file);
		}
		catch (Exception e) {
			throw new IOException(e);
		}
	}

	protected void writeEncryptedXmlFile(RepoTO repoTO, File file, List<CipherSpec> cipherSuites, SaltedSecretKey masterKey) throws IOException,
			CipherException {
		
		ByteArrayOutputStream plaintextRepoOutputStream = new ByteArrayOutputStream();

		try {
			Serializer serializer = new Persister();
			serializer.write(repoTO, plaintextRepoOutputStream);
		}
		catch (Exception e) {
			throw new IOException(e);
		}

		CipherUtil.encrypt(new ByteArrayInputStream(plaintextRepoOutputStream.toByteArray()), new FileOutputStream(file), cipherSuites, masterKey);
	}

	protected String getEncryptedLink(ConnectionTO connectionTO, List<CipherSpec> cipherSuites, SaltedSecretKey masterKey) throws Exception {
		ByteArrayOutputStream plaintextOutputStream = new ByteArrayOutputStream();
		Serializer serializer = new Persister();
		serializer.write(connectionTO, plaintextOutputStream);

		byte[] masterKeySalt = masterKey.getSalt();
		String masterKeySaltEncodedStr = new String(Base58.encode(masterKeySalt));

		byte[] encryptedConnectionBytes = CipherUtil.encrypt(new ByteArrayInputStream(plaintextOutputStream.toByteArray()), cipherSuites, masterKey);
		String encryptedEncodedStorageXml = new String(Base58.encode(encryptedConnectionBytes));

		return "syncany://storage/1/" + masterKeySaltEncodedStr + "/" + encryptedEncodedStorageXml;
	}

	protected String getPlaintextLink(ConnectionTO connectionTO) throws Exception {
		ByteArrayOutputStream plaintextOutputStream = new ByteArrayOutputStream();
		Serializer serializer = new Persister();
		serializer.write(connectionTO, plaintextOutputStream);

		byte[] plaintextStorageXml = plaintextOutputStream.toByteArray();
		String plaintextEncodedStorageXml = new String(Base58.encode(plaintextStorageXml));

		return "syncany://storage/1/not-encrypted/" + plaintextEncodedStorageXml;
	}

	protected void fireNotifyCreateMaster() {
		if (listener != null) {
			listener.onShowMessage("\nCreating master key from password (this might take a while) ...");
		}
	}
	
	protected boolean addToDaemonConfig(File localDir) {
		File daemonConfigFile = new File(UserConfig.getUserConfigDir(), UserConfig.DAEMON_FILE);
		
		if (daemonConfigFile.exists()) {
			try {
				DaemonConfigTO daemonConfigTO = DaemonConfigTO.load(daemonConfigFile);
				String localDirPath = FileUtil.getCanonicalFile(localDir).getAbsolutePath();
				
				// Check if folder already exists
				boolean folderExists = false;
				
				for (FolderTO folderTO : daemonConfigTO.getFolders()) {
					if (localDirPath.equals(folderTO.getPath())) {
						folderExists = true;
						break;
					}
				}
				
				// Add to config if it's not already in there
				if (!folderExists) {					
					logger.log(Level.INFO, "Adding folder to daemon config: " + localDirPath + ", and saving config at " + daemonConfigFile);

					daemonConfigTO.getFolders().add(new FolderTO(localDirPath));							
					DaemonConfigTO.save(daemonConfigTO, daemonConfigFile);
					
					return true;
				}				
			}
			catch (Exception e) {
				logger.log(Level.WARNING, "Adding folder to daemon failed. Ignoring.");
			}

			return false;
		}
		else {
			FolderTO localDirFolderTO = new FolderTO(localDir.getAbsolutePath());			
			UserConfig.createAndWriteDaemonConfig(daemonConfigFile, Arrays.asList(new FolderTO[] { localDirFolderTO }));
			
			return true;
		}
	}
}
