package inz.agents;


import android.content.Context;
import android.content.Intent;
import android.location.Location;

import inz.util.Tuple;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.List;

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
    private Location currLocation;
    private List<Tuple<String, Location>> group;
    private String hostName;
    private Context context;
    private String mode;

    protected void setup() {

        final Object[] args = getArguments();
        if (args[0] instanceof Context) {
            context = (Context) args[0];
        }

        group = new ArrayList<Tuple<String, Location>>() { };

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
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                currLocation = location;
            }
        });

    }

    public void startLocationBroadcast() {

        addBehaviour(new TickerBehaviour(this, 30000) {
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
                    for (Tuple<String, Location> aGroup : group) {
                        msg.addReceiver(new AID(aGroup.getX(), AID.ISLOCALNAME));
                    }
                    try {
                        msg.setContentObject((Serializable) group);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    msg.setConversationId("GROUP UPDATE");
                }


                send(msg);
            }
        });

        addBehaviour(new TickerBehaviour(this, 10000) {
            @Override
            protected void onTick() {
                Intent broadcast = new Intent();
                broadcast.setAction("inz.agents.MobileAgent.GROUP_UPDATE");
                broadcast.putExtra("GROUP", (Serializable) group);
                context.sendBroadcast(broadcast);
            }
        });

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
                        if(group.get(i).getX().equals(msg.getSender().getLocalName()))
                            break;
                    }
                    if (i<group.size()) {
                        try {
                            group.get(i).setY((Location) msg.getContentObject());
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
                MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST), MessageTemplate.MatchConversationId("GROUP UPDATE"));
                ACLMessage msg = myAgent.receive(mt);
                if(msg != null) {
                    try {
                        group = (List<Tuple<String, Location>>) msg.getContentObject();
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
                group.add(new Tuple(msg.getSender().getLocalName(), null));
                ACLMessage response = new ACLMessage(ACLMessage.INFORM);
                for (Tuple<String, Location> aGroup : group) {
                    response.addReceiver(new AID(aGroup.getX(), AID.ISLOCALNAME));
                }
                response.setConversationId("GROUP UPDATE");
                try {
                    response.setContentObject((Serializable) group);
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
