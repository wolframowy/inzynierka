package inz.maptest;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.EditText;

import java.util.logging.Level;

import inz.agents.MobileAgent;
import inz.agents.MobileAgentInterface;
import jade.android.AndroidHelper;
import jade.android.MicroRuntimeService;
import jade.android.MicroRuntimeServiceBinder;
import jade.android.RuntimeCallback;
import jade.core.MicroRuntime;
import jade.core.Profile;
import jade.util.Logger;
import jade.util.leap.Properties;
import jade.wrapper.AgentController;
import jade.wrapper.ControllerException;

public class Menu extends AppCompatActivity {

    private Logger logger = Logger.getJADELogger(this.getClass().getName());

    private boolean isAgentRunning;
    private MobileAgentInterface agentInterface;
    private MicroRuntimeServiceBinder microRuntimeServiceBinder;
    private ServiceConnection serviceConnection;

    private MyReceiver myReceiver;

    private String nickname;    // name of this Agent
    private String mainHost;    // IP address of the main container
    private String mainPort;    // port of the main container
    private String groupHostName;   // name of the host

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isAgentRunning = false;
        setContentView(R.layout.activity_menu);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        myReceiver = new MyReceiver();

        IntentFilter setupFilter = new IntentFilter();
        setupFilter.addAction("inz.agents.MobileAgent.SETUP_COMPLETE");
        registerReceiver(myReceiver, setupFilter);

        

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(myReceiver);

        if (microRuntimeServiceBinder != null)
            microRuntimeServiceBinder.stopAgentContainer(containerShutdownCallback);
        if (serviceConnection != null)
            unbindService(serviceConnection);
        logger.log(Level.INFO, "Destroy activity!");
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(agentInterface == null)
            findViewById(R.id.button_map).setVisibility(View.INVISIBLE);
        else
            findViewById(R.id.button_map).setVisibility(View.VISIBLE);
    }

    private RuntimeCallback<AgentController> agentStartupCallback = new RuntimeCallback<AgentController>() {
        @Override
        public void onSuccess(AgentController newAgent) {
            isAgentRunning = true;
        }

        @Override
        public void onFailure(Throwable throwable) {
            logger.log(Level.INFO, "Nickname already in use!");
            microRuntimeServiceBinder.stopAgentContainer(containerShutdownCallback);
        }
    };

    private RuntimeCallback<Void> containerShutdownCallback = new RuntimeCallback<Void>() {
        @Override
        public void onSuccess(Void var1) {
        }

        @Override
        public void onFailure(Throwable throwable) {
            logger.log(Level.INFO, "Nickname already in use!");
        }
    };

    public void onMapClick(View view) {
        Intent intent = new Intent(this, MapsActivity.class);
        intent.putExtra("AGENT_NICKNAME", nickname);
        startActivity(intent);
    }

    public void onSettingsClick(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }


    public void onStartClick(View view) {

        if(agentInterface != null)
            agentInterface.updateLocation(new Location("test"));

        if (isAgentRunning)
            return;

        EditText groupHost = (EditText) findViewById(R.id.edit_host_name);
        EditText editName = (EditText) findViewById(R.id.edit_name);
        EditText editIp = (EditText) findViewById(R.id.edit_ip);
        EditText editPort = (EditText) findViewById(R.id.edit_port);

        groupHostName = groupHost.getText().toString();
        nickname = editName.getText().toString();
        mainHost = editIp.getText().toString();
        mainPort = editPort.getText().toString();

        if (mainPort.length() != 0 && mainHost.length() != 0 && nickname.length() != 0) {

            final Properties profile = new Properties();
            //profile.setProperty(Profile.CONTAINER_NAME, "TestContainer");
            profile.setProperty(Profile.MAIN_HOST, mainHost); //10.0.2.2 - emulator; 192.168.1.134 -  moj komputer na wifi
            profile.setProperty(Profile.MAIN_PORT, mainPort);
            profile.setProperty(Profile.MAIN, Boolean.FALSE.toString());
            profile.setProperty(Profile.JVM, Profile.ANDROID);

            if (AndroidHelper.isEmulator()) {
                // Emulator: this is needed to work with emulated devices
                profile.setProperty(Profile.LOCAL_HOST, AndroidHelper.LOOPBACK);
                profile.setProperty(Profile.LOCAL_PORT, "8777"); // potrzebne jesli na emulatorze robie (adb forward)
            } else {
                profile.setProperty(Profile.LOCAL_HOST,
                        AndroidHelper.getLocalIPAddress());
            }

            if (microRuntimeServiceBinder == null) {
                serviceConnection = new ServiceConnection() {
                    public void onServiceConnected(ComponentName className,
                                                   IBinder service) {
                        microRuntimeServiceBinder = (MicroRuntimeServiceBinder) service;
                        logger.log(Level.INFO, "Gateway successfully bound to MicroRuntimeService");
                        startContainer(nickname, profile, agentStartupCallback);
                    }

                    public void onServiceDisconnected(ComponentName className) {
                        microRuntimeServiceBinder = null;
                        logger.log(Level.INFO, "Gateway unbound from MicroRuntimeService");
                    }
                };
                logger.log(Level.INFO, "Binding Gateway to MicroRuntimeService...");
                bindService(new Intent(getApplicationContext(),
                                MicroRuntimeService.class), serviceConnection,
                        Context.BIND_AUTO_CREATE);
            } else {
                logger.log(Level.INFO, "MicroRumtimeGateway already binded to service");
                startContainer(nickname, profile, agentStartupCallback);
            }
        } else {
            if (nickname.length() == 0)
                editName.setHint("Fill!");
            if (mainHost.length() == 0)
                editIp.setHint("Fill!");
            if (mainPort.length() == 0)
                editPort.setHint("Fill!");
        }
    }

    private void startContainer(final String nickname, Properties profile,
                                final RuntimeCallback<AgentController> agentStartupCallback) {
        if (!MicroRuntime.isRunning()) {
            microRuntimeServiceBinder.startAgentContainer(profile,
                    new RuntimeCallback<Void>() {
                        @Override
                        public void onSuccess(Void thisIsNull) {
                            logger.log(Level.INFO, "Successfully start of the container...");
                            startAgent(nickname, agentStartupCallback);
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            logger.log(Level.SEVERE, "Failed to start the container...");
                        }
                    });
        } else {
            startAgent(nickname, agentStartupCallback);
        }
    }

    private void startAgent(final String nickname,
                            final RuntimeCallback<AgentController> agentStartupCallback) {

        String agentName = MobileAgent.class.getName();
        Object[] params;
        if (groupHostName.length() != 0) {
            params = new Object[]{this, "Client", groupHostName};
        } else {
            params = new Object[]{this, "Host"};
        }
        microRuntimeServiceBinder.startAgent(nickname,
                agentName,
                params,
                new RuntimeCallback<Void>() {
                    @Override
                    public void onSuccess(Void thisIsNull) {
                        try {
                            agentStartupCallback.onSuccess(MicroRuntime.getAgent(nickname));
                        } catch (ControllerException e) {
                            // Should never happen
                            agentStartupCallback.onFailure(e);
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        agentStartupCallback.onFailure(throwable);
                    }
                });

    }

    private class MyReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            logger.log(Level.INFO, "Received intent " + action);
            try {
                agentInterface = MicroRuntime.getAgent(nickname).getO2AInterface(MobileAgentInterface.class);

            } catch (ControllerException e) {
                findViewById(R.id.button_map).setVisibility(View.INVISIBLE);
                e.printStackTrace();
            }

            if(agentInterface != null)
                findViewById(R.id.button_map).setVisibility(View.VISIBLE);

        }
    }
}