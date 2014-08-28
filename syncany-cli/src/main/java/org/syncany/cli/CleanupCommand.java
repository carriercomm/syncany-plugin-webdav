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
package org.syncany.cli;

import static java.util.Arrays.asList;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.syncany.cli.util.CliUtil;
import org.syncany.database.MultiChunkEntry;
import org.syncany.operations.cleanup.CleanupOperationOptions;
import org.syncany.operations.cleanup.CleanupOperationResult;
import org.syncany.operations.status.StatusOperationOptions;

public class CleanupCommand extends Command {
	@Override
	public CommandScope getRequiredCommandScope() {	
		return CommandScope.INITIALIZED_LOCALDIR;
	}

	@Override
	public int execute(String[] operationArgs) throws Exception {
		CleanupOperationOptions operationOptions = parseOptions(operationArgs);
		CleanupOperationResult operationResult = client.cleanup(operationOptions);

		printResults(operationResult);

		return 0;
	}

	public CleanupOperationOptions parseOptions(String[] operationArgs) throws Exception {
		CleanupOperationOptions operationOptions = new CleanupOperationOptions();

		OptionParser parser = new OptionParser();
		parser.allowsUnrecognizedOptions();
		
		OptionSpec<Void> optionForce = parser.accepts("force");
		OptionSpec<Void> optionNoDatabaseMerge = parser.acceptsAll(asList("M", "no-database-merge"));
		OptionSpec<Void> optionNoOldVersionRemoval = parser.acceptsAll(asList("V", "no-version-remove"));
		OptionSpec<Integer> optionKeepVersions = parser.acceptsAll(asList("k", "keep-versions")).withRequiredArg().ofType(Integer.class);
		OptionSpec<String> optionSecondsBetweenCleanups = parser.acceptsAll(asList("t", "time-between-cleanups")).withRequiredArg().ofType(String.class);
		OptionSpec<Integer> optionMaxDatabaseFiles = parser.acceptsAll(asList("x", "max-database-files")).withRequiredArg().ofType(Integer.class);

		OptionSet options = parser.parse(operationArgs);
		
		// -F, --force
		operationOptions.setForce(options.has(optionForce));
		
		// -M, --no-database-merge
		operationOptions.setMergeRemoteFiles(!options.has(optionNoDatabaseMerge));
		
		// -V, --no-version-removal
		operationOptions.setRemoveOldVersions(!options.has(optionNoOldVersionRemoval));
			
		// -k=<count>, --keep-versions=<count>		
		if (options.has(optionKeepVersions)) {
			int keepVersionCount = options.valueOf(optionKeepVersions);
			
			if (keepVersionCount < 1) {
				throw new Exception("Invalid value for --keep-versions="+keepVersionCount+"; must be >= 1");
			}
			
			operationOptions.setKeepVersionsCount(options.valueOf(optionKeepVersions));			
		}
		
		// -t=<count>, --time-between-cleanups=<count>		
		if (options.has(optionSecondsBetweenCleanups)) {
			long secondsBetweenCleanups = CliUtil.parseTimePeriod(options.valueOf(optionSecondsBetweenCleanups));
			
			if (secondsBetweenCleanups < 0) {
				throw new Exception("Invalid value for --time-between-cleanups="+secondsBetweenCleanups+"; must be >= 0");
			}
			
			operationOptions.setMinSecondsBetweenCleanups(secondsBetweenCleanups);		
		}
		
		// -d=<count>, --max-database-files=<count>
		if (options.has(optionMaxDatabaseFiles)) {
			int maxDatabaseFiles = options.valueOf(optionMaxDatabaseFiles);
			
			if (maxDatabaseFiles < 1) {
				throw new Exception("Invalid value for --max-database-files="+maxDatabaseFiles+"; must be >= 1");
			}
			
			operationOptions.setMaxDatabaseFiles(maxDatabaseFiles);		
		}
		
		// Parse 'status' options
		operationOptions.setStatusOptions(parseStatusOptions(operationArgs));	
		
		// Does this configuration make sense
		boolean nothingToDo = !operationOptions.isMergeRemoteFiles() && operationOptions.isRemoveOldVersions();
		
		if (nothingToDo) {
			throw new Exception("Invalid parameter configuration: -M and -V cannot be set together. Nothing to do.");
		}
		
		return operationOptions;
	}
	
	private StatusOperationOptions parseStatusOptions(String[] operationArgs) {
		StatusCommand statusCommand = new StatusCommand();
		return statusCommand.parseOptions(operationArgs);
	}

	private void printResults(CleanupOperationResult operationResult) {	
		switch (operationResult.getResultCode()) {
		case NOK_DIRTY_LOCAL:
			out.println("Cannot cleanup database if local repository is in a dirty state; Call 'up' first.");
			break;
			
		case NOK_RECENTLY_CLEANED:
			out.println("Cleanup has been done recently, so it is not necessary. If you are sure it is necessary, override with --force.");

		case NOK_LOCAL_CHANGES:
			out.println("Local changes detected. Please call 'up' first'.");
			break;

		case NOK_REMOTE_CHANGES:
			out.println("Remote changes detected or repository is locked by another user. Please call 'down' first.");
			break;
			
		case NOK_OTHER_OPERATIONS_RUNNING:
			out.println("Cannot run cleanup while other clients are performing up/down/cleanup. Try again later.");
			break;

		case OK:
			if (operationResult.getMergedDatabaseFilesCount() > 0) {
				out.println(operationResult.getMergedDatabaseFilesCount() + " database files merged.");
			}
			
			if (operationResult.getRemovedMultiChunks().size() > 0) {
				long totalRemovedMultiChunkSize = 0;
				
				for (MultiChunkEntry removedMultiChunk : operationResult.getRemovedMultiChunks().values()) {
					totalRemovedMultiChunkSize += removedMultiChunk.getSize();
				}
				
				out.printf("%d multichunk(s) deleted on remote storage (freed %.2f MB)\n", 
					operationResult.getRemovedMultiChunks().size(), (double) totalRemovedMultiChunkSize / 1024 / 1024);
			}

			if (operationResult.getRemovedOldVersionsCount() > 0) {
				out.println(operationResult.getRemovedOldVersionsCount() + " file histories shortened.");
				// TODO [low] This counts only the file histories, not file versions; not very helpful!
			}

			out.println("Cleanup successful.");			
			break;

		case OK_NOTHING_DONE:
			out.println("Cleanup not necessary. Nothing done.");
			break;

		default:
			throw new RuntimeException("Invalid result code: " + operationResult.getResultCode().toString());
		}	
	}
	

}
