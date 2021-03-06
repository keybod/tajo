/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tajo.cli.tools;

import com.google.protobuf.ServiceException;
import org.apache.commons.cli.*;
import org.apache.tajo.auth.UserRoleInfo;
import org.apache.tajo.catalog.*;
import org.apache.tajo.catalog.proto.CatalogProtos;
import org.apache.tajo.client.TajoClient;
import org.apache.tajo.client.TajoClientImpl;
import org.apache.tajo.conf.TajoConf;
import org.apache.tajo.service.ServiceTrackerFactory;
import org.apache.tajo.util.NetUtils;
import org.apache.tajo.util.Pair;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class TajoDump {
  private static final org.apache.commons.cli.Options options;

  static {
    options = new Options();
    options.addOption("h", "host", true, "Tajo server host");
    options.addOption("p", "port", true, "Tajo server port");
    options.addOption("a", "all", false, "dump all table DDLs");
  }

  private static void printUsage() {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp( "tajo-dump [options] [database name]", options);
  }

  private static Pair<String, Integer> getConnectionAddr(TajoConf conf, CommandLine cmd) {
    String hostName = null;
    Integer port = null;
    if (cmd.hasOption("h")) {
      hostName = cmd.getOptionValue("h");
    }
    if (cmd.hasOption("p")) {
      port = Integer.parseInt(cmd.getOptionValue("p"));
    }

    InetSocketAddress address = conf.getSocketAddrVar(TajoConf.ConfVars.TAJO_MASTER_CLIENT_RPC_ADDRESS,
        TajoConf.ConfVars.TAJO_MASTER_UMBILICAL_RPC_ADDRESS);

    if(hostName == null) {
      hostName = address.getHostName();
    }

    if (port == null) {
      port = address.getPort();
    }

    return new Pair<>(hostName, port);
  }

  public static void main(String [] args) throws ParseException, IOException, ServiceException, SQLException {
    final TajoConf conf = new TajoConf();
    final CommandLineParser parser = new PosixParser();
    final CommandLine cmd = parser.parse(options, args);
    final Pair<String, Integer> hostAndPort = getConnectionAddr(conf, cmd);
    final String hostName = hostAndPort.getFirst();
    final Integer port = hostAndPort.getSecond();
    final UserRoleInfo userInfo = UserRoleInfo.getCurrentUser();

    String baseDatabaseName = null;
    if (cmd.getArgList().size() > 0) {
      baseDatabaseName = (String) cmd.getArgList().get(0);
    }

    boolean isDumpingAllDatabases = cmd.hasOption('a');

    // Neither two choices
    if (!isDumpingAllDatabases && baseDatabaseName == null) {
      printUsage();
      System.exit(-1);
    }

    TajoClient client = null;
    if ((hostName == null) ^ (port == null)) {
      System.err.println("ERROR: cannot find any TajoMaster rpc address in arguments and tajo-site.xml.");
      System.exit(-1);
    } else if (hostName != null && port != null) {
      conf.setVar(TajoConf.ConfVars.TAJO_MASTER_CLIENT_RPC_ADDRESS, NetUtils.getHostPortString(hostName, port));
      client = new TajoClientImpl(ServiceTrackerFactory.get(conf));
    } else {
      client = new TajoClientImpl(ServiceTrackerFactory.get(conf));
    }

    PrintWriter writer = new PrintWriter(System.out);
    dump(client, userInfo, baseDatabaseName, isDumpingAllDatabases, true, true, writer);

    System.exit(0);
  }
  
  private static boolean isAcceptableDumpingDatabase(String databaseName) {
    return (databaseName != null && !databaseName.equalsIgnoreCase(CatalogConstants.INFORMATION_SCHEMA_DB_NAME));
  }

  public static void dump(TajoClient client, UserRoleInfo userInfo, String baseDatabaseName,
                   boolean isDumpingAllDatabases, boolean includeUserName, boolean includeDate, PrintWriter out)
      throws SQLException, ServiceException {
    printHeader(out, userInfo, includeUserName, includeDate);

    if (isDumpingAllDatabases) {
      // sort database names in an ascending lexicographic order of the names.
      List<String> sorted = new ArrayList<>(client.getAllDatabaseNames());
      Collections.sort(sorted);

      for (String databaseName : sorted) {
        if (isAcceptableDumpingDatabase(databaseName)) {
          dumpDatabase(client, databaseName, out);
        }
      }
    } else {
      if (isAcceptableDumpingDatabase(baseDatabaseName)) {
        dumpDatabase(client, baseDatabaseName, out);
      }
    }
    out.flush();
  }

  private static void printHeader(PrintWriter writer, UserRoleInfo userInfo, boolean includeUSerName,
                                  boolean includeDate) {
    writer.write("--\n");
    writer.write("-- Tajo database dump\n");
    if (includeUSerName) {
      writer.write("--\n-- Dump user: " + userInfo.getUserName() + "\n");
    }
    if (includeDate) {
      writer.write("--\n-- Dump date: " + toDateString() + "\n");
    }
    writer.write("--\n");
    writer.write("\n");
  }

  private static void dumpDatabase(TajoClient client, String databaseName, PrintWriter writer)
      throws SQLException, ServiceException {
    writer.write("\n");
    writer.write("--\n");
    writer.write(String.format("-- Database name: %s%n", CatalogUtil.denormalizeIdentifier(databaseName)));
    writer.write("--\n");
    writer.write("\n");
    writer.write(String.format("CREATE DATABASE IF NOT EXISTS %s;", CatalogUtil.denormalizeIdentifier(databaseName)));
    writer.write("\n\n");

    // returned list is immutable.
    List<String> tableNames = new ArrayList<>(client.getTableList(databaseName));
    Collections.sort(tableNames);
    for (String tableName : tableNames) {
      try {
        String fqName = CatalogUtil.buildFQName(databaseName, tableName);
        TableDesc table = client.getTableDesc(fqName);

        if (table.getMeta().getDataFormat().equalsIgnoreCase("SYSTEM")) {
          continue;
        }
        
        if (table.isExternal()) {
          writer.write(DDLBuilder.buildDDLForExternalTable(table));
        } else {
          writer.write(DDLBuilder.buildDDLForBaseTable(table));
        }
        if (table.hasPartition()) {
          writer.write("\n\n");
          writer.write("--\n");
          writer.write(String.format("-- Table Partitions: %s%n", tableName));
          writer.write("--\n");
          // TODO: This should be improved at TAJO-1891
//          List<PartitionDescProto> partitionProtos = client.getPartitionsOfTable(fqName);
//          for (PartitionDescProto eachPartitionProto : partitionProtos) {
//            writer.write(DDLBuilder.buildDDLForAddPartition(table, eachPartitionProto));
//          }
          writer.write(String.format("ALTER TABLE %s REPAIR PARTITION;",
            CatalogUtil.denormalizeIdentifier(databaseName) + "." + CatalogUtil.denormalizeIdentifier(tableName)));

          writer.write("\n\n");
        }

        if (client.hasIndexes(tableName)) {
          List<CatalogProtos.IndexDescProto> indexeProtos = client.getIndexes(tableName);
          for (CatalogProtos.IndexDescProto eachIndexProto : indexeProtos) {
            writer.write("\n\n");
            writer.write(DDLBuilder.buildDDLForIndex(new IndexDesc(eachIndexProto)));
          }
        }
        writer.write("\n\n");
      } catch (Throwable e) {
        // dump for each table can throw any exception. We need to skip the exception case.
        // here, the error message prints out via stderr.
        System.err.println("ERROR:" + tableName + "," + e.getMessage());
      }
    }
  }

  private static String toDateString() {
    DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    java.util.Date today = Calendar.getInstance().getTime();
    return df.format(today);
  }
}
