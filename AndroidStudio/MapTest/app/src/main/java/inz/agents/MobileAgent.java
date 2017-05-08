package inz.agents;


import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Parcelable;

import inz.util.AgentPos;
import inz.util.ParcelableLatLng;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;


import jade.core.AID;
import jade.core.Agent;

import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.util.Logger;

/**
 * Created by Kuba on 20/04/2017.
 * Agent for phones
 */

public class MobileAgent extends Agent implements MobileAgentInterface {

    private Logger logger = Logger.getJADELogger(this.getClass().getName());
    private ParcelableLatLng currLocation;
    private ArrayList<AgentPos> group;
    private String hostName;
    private Context context;
    private String mode;

    protected void setup() {

        final Object[] args = getArguments();
        if (args[0] instanceof Context) {
            context = (Context) args[0];
        }


        group = new ArrayList<AgentPos>(){};
        group.add(new AgentPos(this.getLocalName(), null));

        mode = (String) args[1];
        /**
         *  If it's a host
         */
        if(mode == "Host") {
            setupHost(args);
        }
        /**
         *  If it's a client
         */
        else if (mode == "Client") {
            setupClient(args);
        }
        /**
         *  If it's an error, shut down the agent
         */
        else {
            takeDown();
            return;
        }

        registerO2AInterface(MobileAgentInterface.class, this);

        Intent broadcast = new Intent();
        broadcast.setAction("inz.agents.MobileAgent.SETUP_COMPLETE");
        context.sendBroadcast(broadcast);

    }

    protected void takeDown() {

    }


    public void updateLocation(final Location location) {
        if(mode.equals("Client"))
            addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                currLocation = new ParcelableLatLng(location.getLatitude(), location.getLongitude());
            }
        });
        else if(mode.equals("Host"))
            addBehaviour(new OneShotBehaviour() {
                @Override
                public void action() {
                    int i;
                    for( i=0; i<group.size();++i) {
                        if(group.get(i).getName().equals(this.getAgent().getLocalName()))
                            break;
                    }
                    if (i<group.size())
                        group.get(i).setLatLng(new ParcelableLatLng(location.getLatitude(), location.getLongitude()));
                }
            });

    }

    public void startLocationBroadcast() {

        addBehaviour(new TickerBehaviour(this, 20000) {
            @Override
            protected void onTick() {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                if(mode.equals("Client")) {
                    msg.addReceiver(new AID(hostName, AID.ISLOCALNAME));
                    try {
                        msg.setContentObject((Serializable) currLocation);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    msg.setConversationId("LOCATION UPDATE");
                }
                else if(mode.equals("Host")){
                    for (AgentPos aGroup : group) {
                        msg.addReceiver(new AID(aGroup.getName(), AID.ISLOCALNAME));
                    }
                    try {
                        msg.setContentObject(group.toArray());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    msg.setConversationId("GROUP UPDATE");
                }


                send(msg);
            }
        });

        addBehaviour(new TickerBehaviour(this, 30000) {
            @Override
            protected void onTick() {
                Intent broadcast = new Intent();
                broadcast.setAction("inz.agents.MobileAgent.GROUP_UPDATE");
                context.sendBroadcast(broadcast);
            }
        });

    }

    public ArrayList<AgentPos> getGroup() {
        ArrayList<AgentPos> othersGroup = new ArrayList<AgentPos>(group);
        for(int i=0; i<othersGroup.size(); ++i) {
            if(othersGroup.get(i).getName().equals(this.getLocalName())) {
                othersGroup.remove(i);
                break;
            }
        }
        return othersGroup;
    }

    private void setupHost(Object[] args) {
        // Behaviour updating group with new members
        addBehaviour(new registerBehaviour());
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId("LOCATION UPDATE"));
                ACLMessage msg = myAgent.receive(mt);
                if(msg != null) {
                    int i;
                    for( i=0; i<group.size();++i) {
                        if(group.get(i).getName().equals(msg.getSender().getLocalName()))
                            break;
                    }
                    if (i<group.size()) {
                        try {
                            group.get(i).setLatLng((ParcelableLatLng) msg.getContentObject());
                        } catch (UnreadableException e) {
                            e.printStackTrace();
                        }
                    }
                }
                else
                    block();
            }
        });
    }

    private void setupClient(Object[] args) {
        hostName = args[2].toString();
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(new AID(hostName, AID.ISLOCALNAME));
                msg.setConversationId("Registration");
                send(msg);
            }
        });

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId("GROUP UPDATE"));
                ACLMessage msg = myAgent.receive(mt);
                if(msg != null) {
                    try {
                        Object[] c =(Object[]) msg.getContentObject();
                        group = new ArrayList<>();
                        for(Object obj: c) {
                            group.add((AgentPos) obj);
                        }

                    } catch (UnreadableException e) {
                        e.printStackTrace();
                    }
                }
                else
                    block();
            }
        });
    }

    /**
     * Behaviour updating group with new members
     */
    private class registerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST), MessageTemplate.MatchConversationId("Registration"));
            ACLMessage msg = myAgent.receive(mt);
            if(msg != null) {
                group.add(new AgentPos(msg.getSender().getLocalName(), null));
                ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                for (AgentPos aGroup : group) {
                    if(aGroup.getName() != this.getAgent().getLocalName())
                        response.addReceiver(new AID(aGroup.getName(), AID.ISLOCALNAME));
                }
                response.setConversationId("GROUP UPDATE");
                try {
                    response.setContentObject( group.toArray());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                send(response);
            }
            else
                block();

        }
    }

}
