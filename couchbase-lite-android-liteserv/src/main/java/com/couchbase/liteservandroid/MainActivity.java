package com.couchbase.liteservandroid;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseOptions;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Status;
import com.couchbase.lite.View;
import com.couchbase.lite.android.AndroidContext;
import com.couchbase.lite.javascript.JavaScriptReplicationFilterCompiler;
import com.couchbase.lite.javascript.JavaScriptViewCompiler;
import com.couchbase.lite.listener.Credentials;
import com.couchbase.lite.listener.LiteListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Properties;

import Acme.Serve.SSLAcceptor;
import Acme.Serve.Serve;

public class MainActivity extends Activity {
    private static final boolean SSL = true;
    private static final int DEFAULT_LISTEN_PORT = 5984;
    private static final String DATABASE_NAME = "cblite-test";
    private static final String LISTEN_PORT_PARAM_NAME = "listen_port";
    private static final String LISTEN_LOGIN_PARAM_NAME = "username";
    private static final String LISTEN_PASSWORD_PARAM_NAME = "password";
    private static final String DB_PASSWORD_PARAM_NAME = "dbpassword";
    private static final String STORAGE_TYPE_PARAM_NAME = "storage";
    private static final String TAG = "LiteServ";

    private String dbPassword = null;
    private Credentials allowedCredentials;
    private String storageType = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // storage
        ApplicationInfo info = null;
        try {
            info = getApplicationContext().getPackageManager().getApplicationInfo(getApplicationContext().getPackageName(), PackageManager.GET_META_DATA);
            storageType = (String) info.metaData.get("storage");
            dbPassword = (String) info.metaData.get("dbpassword");
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "failed to obtain meta data from AndroidManifest.xml", e);
            storageType = "sqlite";
            dbPassword = null;
        }

        if (getStorageType() != null)
            storageType = getStorageType();
        if(getDBPassword() != null)
            dbPassword = getDBPassword();

        Log.i(TAG, "storageType=" + storageType);
        Log.i(TAG, "dbpassword=" + dbPassword);

        // Register the JavaScript view compiler
        View.setCompiler(new JavaScriptViewCompiler());
        Database.setFilterCompiler(new JavaScriptReplicationFilterCompiler());

        try {
            int port = startCBLListener(getListenPort());
            showListenPort(port);
            showListenCredentials();
        } catch (Exception e) {
            TextView listenPortTextView = (TextView) findViewById(R.id.listen_port_textview);
            listenPortTextView.setText(String.format("Error starting LiteServ"));
            Log.e(TAG, "Error starting LiteServ", e);
        }
    }

    private void showListenPort(int listenPort) {
        Log.d(TAG, "listenPort: " + listenPort);
        TextView listenPortTextView = (TextView) findViewById(R.id.listen_port_textview);
        listenPortTextView.setText(String.format(
                "Listening on port: %d.  Db: %s Storage: %s dbpasword: %s",
                listenPort, DATABASE_NAME, storageType,
                dbPassword != null && dbPassword.length() > 0 ? "yes" : "no"));
    }

    private void showListenCredentials() {
        TextView listenCredentialsTextView = (TextView) findViewById(R.id.listen_credentials_textview);
        String credentialsDisplay = String.format(
                "login: %s password: %s",
                allowedCredentials.getLogin(),
                allowedCredentials.getPassword()
        );
        Log.v(TAG, credentialsDisplay);
        listenCredentialsTextView.setText(credentialsDisplay);
    }

    private int startCBLListener(int suggestedListenPort) throws IOException, CouchbaseLiteException {
        Manager.enableLogging(TAG, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG_SYNC_ASYNC_TASK, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG_SYNC, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG_QUERY, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG_VIEW, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG_DATABASE, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG_BATCHER, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG_ROUTER, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG_CHANGE_TRACKER, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG_LISTENER, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG_REMOTE_REQUEST, com.couchbase.lite.util.Log.VERBOSE);

        Manager manager = startCBLite();
        startDatabase(manager, DATABASE_NAME);

        allowedCredentials = new Credentials("", "");

        LiteListener listener;
        // HTTPS (SSL)
        if (SSL) {
            File certificateFile = new File(this.getFilesDir(), "certificate.pfx");
            if (!certificateFile.exists()) {
                if (!extractCertificateFromAsset(certificateFile))
                    throw new CouchbaseLiteException("Unable to extract crificate file from asset", Status.INTERNAL_SERVER_ERROR);
            }
            Properties props = new Properties();
            props.setProperty(Serve.ARG_ACCEPTOR_CLASS, "Acme.Serve.SSLAcceptor");
            props.setProperty(SSLAcceptor.ARG_KEYSTORETYPE, "PKCS12");
            props.setProperty(SSLAcceptor.ARG_KEYSTOREFILE, certificateFile.getAbsolutePath());
            props.setProperty(SSLAcceptor.ARG_KEYSTOREPASS, "cbmobile");
            props.setProperty(SSLAcceptor.ARG_PORT, String.valueOf(suggestedListenPort));
            listener = new LiteListener(manager, suggestedListenPort, allowedCredentials, props);
        }
        // HTTP
        else {
            listener = new LiteListener(manager, suggestedListenPort, allowedCredentials);
        }
        int port = listener.getListenPort();
        Thread thread = new Thread(listener);
        thread.start();
        return port;
    }

    protected Manager startCBLite() throws IOException {
        Manager manager = new Manager(new AndroidContext(this), Manager.DEFAULT_OPTIONS);
        manager.setStorageType(storageType);
        if (dbPassword != null && dbPassword.length() > 0) {
            String[] passwords = dbPassword.split(",");
            for (String password : passwords) {
                String[] items = password.split(":");
                manager.registerEncryptionKey(items[1], items[0]);
            }
        }
        return manager;
    }

    protected void startDatabase(Manager manager, String databaseName) throws CouchbaseLiteException {
        DatabaseOptions options = new DatabaseOptions();
        options.setCreate(true);
        options.setStorageType(storageType);
        if (dbPassword != null && dbPassword.length() > 0) {
            Map keys = manager.getEncryptionKeys();
            if (keys.containsKey(DATABASE_NAME))
                options.setEncryptionKey(keys.get(DATABASE_NAME));
        }
        Database database = manager.openDatabase(DATABASE_NAME, options);
        database.open();
    }

    private int getListenPort() {
        return getIntent().getIntExtra(LISTEN_PORT_PARAM_NAME, DEFAULT_LISTEN_PORT);
    }

    private String getLogin() {
        return getIntent().getStringExtra(LISTEN_LOGIN_PARAM_NAME);
    }

    private String getPassword() {
        return getIntent().getStringExtra(LISTEN_PASSWORD_PARAM_NAME);
    }

    private String getDBPassword() {
        return getIntent().getStringExtra(DB_PASSWORD_PARAM_NAME);
    }

    private String getStorageType() {
        return getIntent().getStringExtra(STORAGE_TYPE_PARAM_NAME);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private boolean extractCertificateFromAsset(File ceritificateFile) {
        InputStream is = null;
        try {
            is = getAssets().open("certificate.pfx");
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        OutputStream os = null;
        try {
            os = new FileOutputStream(ceritificateFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }

        byte[] buffer = new byte[1024];
        int read;
        try {
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
