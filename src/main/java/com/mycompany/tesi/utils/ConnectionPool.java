/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mycompany.tesi.utils;

import java.io.File;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

//
// Here are the dbcp-specific classes.
// Note that they are only used in the setupDataSource
// method. In normal use, your classes interact
// only with the standard JDBC API
//
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.commons.dbcp.ConnectionFactory;
import org.apache.commons.dbcp.PoolingDataSource;
import org.apache.commons.dbcp.PoolableConnectionFactory;
import org.apache.commons.dbcp.DriverManagerConnectionFactory;
import org.apache.commons.pool.impl.GenericObjectPool.Config;

//
// Here's a simple example of how to use the PoolingDataSource.
// In this example, we'll construct the PoolingDataSource manually,
// just to show how the pieces fit together, but you could also
// configure it using an external configuration file in
// JOCL format (and eventually Digester).
//

//
// Note that this example is very similar to the PoolingDriver
// example.  In fact, you could use the same pool in both a
// PoolingDriver and a PoolingDataSource
//

//
// To compile this example, you'll want:
//  * commons-pool-1.5.4.jar
//  * commons-dbcp-1.2.2.jar
//  * j2ee.jar (for the javax.sql classes)
// in your classpath.
//
// To run this example, you'll want:
//  * commons-pool-1.5.6.jar
//  * commons-dbcp-1.3.jar (JDK 1.4-1.5) or commons-dbcp-1.4 (JDK 1.6+)
//  * j2ee.jar (for the javax.sql classes)
//  * the classes for your (underlying) JDBC driver
// in your classpath.
//
// Invoke the class using two arguments:
//  * the connect string for your underlying JDBC driver
//  * the query you'd like to execute
// You'll also want to ensure your underlying JDBC driver
// is registered.  You can use the "jdbc.drivers"
// property to do this.
//
// For example:
//  java -Djdbc.drivers=oracle.jdbc.driver.OracleDriver \
//       -classpath commons-pool-1.5.6.jar:commons-dbcp-1.4.jar:j2ee.jar:oracle-jdbc.jar:. \
//       ManualPoolingDataSourceExample \
//       "jdbc:oracle:thin:scott/tiger@myhost:1521:mysid" \
//       "SELECT * FROM DUAL"
//
public class ConnectionPool {

    private final DataSource dataSource;
    private ObjectPool pool;
    public static final String JDBC_URI = "jdbc:postgresql://127.0.0.1:5432/";//192.168.128.128
    
    static {
        //
        // First we load the underlying JDBC driver.
        // You need this if you don't use the jdbc.drivers
        // system property.
        //
        
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(ConnectionPool.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(0);
        }
    }
    
    public ConnectionPool(final String dbName, int poolSize) {
        this.dataSource = setupDataSource(JDBC_URI + dbName, "postgres", "postgres", poolSize);
    }
    
    public void close() {
        try {
            pool.close();
        } catch (Exception ex) {
            Logger.getLogger(ConnectionPool.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public DataSource getDataSource() {
        return dataSource;
    }
    
    public static void main(String[] args) {
        //
        // Then, we set up the PoolingDataSource.
        // Normally this would be handled auto-magically by
        // an external configuration, but in this example we'll
        // do it manually.
        //
        System.out.println("Setting up data source.");
        
        DataSource dataSource = new ConnectionPool("berlin_routing", 10).getDataSource();
        System.out.println("Done.");

        //
        // Now, we can use JDBC DataSource as we normally would.
        //
        Connection conn = null;
        Statement stmt = null;
        ResultSet rset = null;

        try {
            System.out.println("Creating connection.");
            conn = dataSource.getConnection();
            System.out.println("Creating statement.");
            stmt = conn.createStatement();
            System.out.println("Executing statement.");
            rset = stmt.executeQuery("SELECT * FROM ways LIMIT 1");
            System.out.println("Results:");
            int numcols = rset.getMetaData().getColumnCount();
            while(rset.next()) {
                for(int i=1;i<=numcols;i++) {
                    System.out.print("\t" + rset.getString(i));
                }
                System.out.println("");
            }
        } catch(SQLException e) {
            e.printStackTrace();
        } finally {
            try { if (rset != null) rset.close(); } catch(Exception e) { }
            try { if (stmt != null) stmt.close(); } catch(Exception e) { }
            try { if (conn != null) conn.close(); } catch(Exception e) { }
        }
    }

    private DataSource setupDataSource(String connectURI, String uname, String passwd, int poolSize) {
        //
        // First, we'll need a ObjectPool that serves as the
        // actual pool of connections.
        //
        // We'll use a GenericObjectPool instance, although
        // any ObjectPool implementation will suffice.
        //
        Config config = new Config();
        config.maxActive = poolSize;
        pool = new GenericObjectPool(null, config);

        //
        // Next, we'll create a ConnectionFactory that the
        // pool will use to create Connections.
        // We'll use the DriverManagerConnectionFactory,
        // using the connect string passed in the command line
        // arguments.
        //
        ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(connectURI,uname, passwd);

        //
        // Now we'll create the PoolableConnectionFactory, which wraps
        // the "real" Connections created by the ConnectionFactory with
        // the classes that implement the pooling functionality.
        //
        PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory,pool,null,null,false,true);

        //
        // Finally, we create the PoolingDriver itself,
        // passing in the object pool we created.
        //
        PoolingDataSource dataSource = new PoolingDataSource(pool);

        return dataSource;
    }
}