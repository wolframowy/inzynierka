package inz.agents;


import android.content.Context;
import android.content.Intent;
import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

import inz.util.AgentPos;
import inz.util.ParcelableLatLng;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;


import jade.core.AID;
import jade.core.Agent;

import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.FIPAAgentManagement.AMSAgentDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.domain.AMSService;

/**
 * Created by Kuba on 20/04/2017.
 * Agent for phones
 */


public class MobileAgent extends Agent implements MobileAgentInterface {

    private final int MAX_VOTES = 5;

    private final String REGISTRATION_ID = "Registration";
    private final String DEREGISTRATION_ID = "Deregister";
    private final String LOCATION_UPDATE_ID = "LOCATION UPDATE";
    private final String GROUP_UPDATE_ID = "GROUP UPDATE";
    private final String CENTER_UPDATE_ID = "CENTER UPDATE";
    private final String CHANGE_STATE_ID = "CHANGE STATE";
    private final String ADD_PLACE_ID = "ADD PLACE";
    private final String PLACES_ID = "PLACES";
    private final String ADD_VOTE_ID = "ADD VOTE";
    private final String DESTINATION_ID = "DESTINATION CHOSEN";
    private final String HOST_LEAVE_ID = "HOST LEAVE";

    private ParcelableLatLng currLocation;
    private ArrayList<AgentPos> group;
    private ArrayList<AgentPos> selectedPlaces;
    private HashMap<String, Integer> votes;
    private String hostName;
    private Context context;
    private String mode;
    private AgentPos center;
    private AgentPos destination;
    private int votesLeft = MAX_VOTES;

    private State state = State.GATHER;

    /***********************************************
     ***********************************************
     **************  INNER METHODS  ****************
     ***********************************************
     ***********************************************/

    protected void setup() {

        final Object[] args = getArguments();
        if (args[0] instanceof Context) {
            context = (Context) args[0];
        }


        group = new ArrayList<AgentPos>(){};
        group.add(new AgentPos(this.getLocalName(), null));

        selectedPlaces = new ArrayList<AgentPos>() {};

        mode = (String) args[1];

        switch (mode) {
            case "Host":
                votes = new HashMap<String, Integer>() {};
                setupHost();
                break;

            case "Client":
                setupClient(args);
                break;

            default:
                takeDown();
                return;
        }

        registerO2AInterface(MobileAgentInterface.class, this);

        Intent broadcast = new Intent();
        broadcast.setAction("inz.agents.MobileAgent.SETUP_COMPLETE");
        context.sendBroadcast(broadcast);

    }

    protected void takeDown() {
        if(mode.equals("Client")){
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID(hostName, AID.ISLOCALNAME));
            msg.setConversationId(DEREGISTRATION_ID);
            send(msg);
        }
        else if(mode.equals("Host")) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            for (AgentPos aGroup : group) {
                if(!aGroup.getName().equals(this.getLocalName()))
                    msg.addReceiver(new AID(aGroup.getName(), AID.ISLOCALNAME));
            }
            msg.setConversationId(HOST_LEAVE_ID);
            send(msg);
        }
    }

    private void setupHost() {
        // Behaviour updating group with new members
        addBehaviour(new registerBehaviour());
        addBehaviour(new deRegisterBehaviour());
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId(LOCATION_UPDATE_ID));
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
        try {
            SearchConstraints c = new SearchConstraints();
            c.setMaxResults (new Long(-1));
            AMSAgentDescription[] agents = AMSService.search(this, new AMSAgentDescription(), c );
            Boolean notFound = true;
            for(AMSAgentDescription anAgent : agents) {
                if(anAgent.getName().equals(new AID(hostName, AID.ISLOCALNAME))){
                    notFound = false;
                    break;
                }
            }
            if(notFound) {
                Intent broadcast = new Intent();
                broadcast.setAction("inz.agents.MobileAgent.HOST_NOT_FOUND");
                context.sendBroadcast(broadcast);
                this.doDelete();
                return;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(new AID(hostName, AID.ISLOCALNAME));
                msg.setConversationId(REGISTRATION_ID);
                send(msg);
            }
        });

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId(GROUP_UPDATE_ID));
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
                    Intent broadcast = new Intent();
                    broadcast.setAction("inz.agents.MobileAgent.GROUP_UPDATE");
                    context.sendBroadcast(broadcast);
                }
                else
                    block();
            }
        });

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId(CHANGE_STATE_ID));
                ACLMessage msg = myAgent.receive(mt);
                if(msg != null) {
                    try {
                        state = (State) msg.getContentObject();
                    } catch (UnreadableException e) {
                        e.printStackTrace();
                    }

                    Intent broadcast = new Intent();
                    broadcast.setAction("inz.agents.MobileAgent.STATE_CHANGED");
                    broadcast.putExtra("State", state);
                    context.sendBroadcast(broadcast);

                    switch (state) {
                        case CHOOSE:
                            addBehaviour(new getSelectedPlaceBehaviour());
                            break;
                        case VOTE:
                            break;
                        case LEAD:
                            addBehaviour(new getDestinationBehaviour());
                            break;
                    }

                }
                else
                    block();
            }
        });

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId(HOST_LEAVE_ID));
                ACLMessage msg = myAgent.receive(mt);
                if(msg != null) {
                    this.myAgent.doDelete();

                    Intent broadcast = new Intent();
                    broadcast.setAction("inz.agents.MobileAgent.HOST_LEFT");
                    context.sendBroadcast(broadcast);

                }
                else
                    block();
            }
        });
    }

    private void calculateCenter () {
        addBehaviour(new TickerBehaviour(this, 10000) {
            @Override
            public void onTick() {
                double x = 0.0;
                double y = 0.0;
                for (AgentPos ap : group) {
                    if (ap.getLatLng() == null)
                        return;
                    x += ap.getLatLng().latitude;
                    y += ap.getLatLng().longitude;
                }

                x = x / group.size();
                y = y / group.size();

                center = new AgentPos("Center", new ParcelableLatLng(x, y));//(rLat*(180/Math.PI), rLon*(180/Math.PI)));

                myAgent.addBehaviour(new OneShotBehaviour() {
                    @Override
                    public void action() {
                        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);

                        for (AgentPos aGroup : group) {
                            msg.addReceiver(new AID(aGroup.getName(), AID.ISLOCALNAME));
                        }
                        try {
                            msg.setContentObject(center);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        msg.setConversationId(CENTER_UPDATE_ID);

                        send(msg);
                    }
                });

                Intent broadcast = new Intent();
                broadcast.setAction("inz.agents.MobileAgent.CENTER_CALCULATED");
                context.sendBroadcast(broadcast);
                this.stop();
            }
        });
    }

    /***********************************************
     ***********************************************
     ************  INTERFACE METHODS  **************
     ***********************************************
     ***********************************************/


    public void updateLocation(final Location location) {
        if(mode.equals("Client")) {
            addBehaviour(new OneShotBehaviour() {
                @Override
                public void action() {
                    currLocation = new ParcelableLatLng(location.getLatitude(), location.getLongitude());
                }
            });
        }
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

        addBehaviour(new TickerBehaviour(this, 10000) {
            @Override
            protected void onTick() {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                if(mode.equals("Client")) {
                    msg.addReceiver(new AID(hostName, AID.ISLOCALNAME));
                    try {
                        msg.setContentObject(currLocation);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    msg.setConversationId(LOCATION_UPDATE_ID);
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
                    msg.setConversationId(GROUP_UPDATE_ID);
                }


                send(msg);
            }
        });

        addBehaviour(new TickerBehaviour(this, 10000) {
            @Override
            protected void onTick() {
                Intent broadcast = new Intent();
                broadcast.setAction("inz.agents.MobileAgent.GROUP_UPDATE");
                context.sendBroadcast(broadcast);
            }
        });

        if(this.mode.equals("Host")) {
            calculateCenter();
        }
        else if(this.mode.equals("Client")) {
            addBehaviour(new CyclicBehaviour() {
                @Override
                public void action() {
                    MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId(CENTER_UPDATE_ID));
                    ACLMessage msg = myAgent.receive(mt);
                    if(msg != null) {
                        try {
                            center = (AgentPos) msg.getContentObject();
                        } catch (UnreadableException e) {
                            e.printStackTrace();
                        }
                        Intent broadcast = new Intent();
                        broadcast.setAction("inz.agents.MobileAgent.CENTER_UPDATED");
                        context.sendBroadcast(broadcast);
                    }
                    else
                        block();
                }
            });

        }
    }



    public ArrayList<AgentPos> getGroup() {
        ArrayList<AgentPos> othersGroup = new ArrayList<>(group);
        for(int i=0; i<othersGroup.size(); ++i) {
            if(othersGroup.get(i).getName().equals(this.getLocalName())) {
                othersGroup.remove(i);
                break;
            }
        }
        return othersGroup;
    }

    public AgentPos getCenter() {
        return center;
    }

    public void setCenter(LatLng newCenterPos) {
        center.setLatLng(new ParcelableLatLng(newCenterPos.latitude, newCenterPos.longitude));

        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);

                for (AgentPos aGroup : group) {
                    if(!aGroup.getName().equals(myAgent.getLocalName()))
                        msg.addReceiver(new AID(aGroup.getName(), AID.ISLOCALNAME));
                }
                try {
                    msg.setContentObject(center);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                msg.setConversationId(CENTER_UPDATE_ID);

                send(msg);
            }
        });
    }

    public void changeState(State newState) {
        this.state = newState;
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                for (AgentPos aGroup : group) {
                    if(!aGroup.getName().equals(myAgent.getLocalName()))
                        msg.addReceiver(new AID(aGroup.getName(), AID.ISLOCALNAME));
                }
                msg.setConversationId(CHANGE_STATE_ID);
                try {
                    msg.setContentObject(state);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                send(msg);

                switch (state) {
                    case CHOOSE:
                        addBehaviour(new getPlaceBehaviour());
                        break;
                    case VOTE:
                        addBehaviour(new getVoteBehaviour());
                        break;
                    case LEAD:
                        if(mode.equals("Client"))
                            addBehaviour(new getDestinationBehaviour());
                        break;
                }

            }
        });
    }

    public void addPlace(String name, LatLng pos) {
        AgentPos newPlace = new AgentPos(name, new ParcelableLatLng(pos));
        selectedPlaces.add(newPlace);
        if(mode.equals("Client")) {

            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID(hostName, AID.ISLOCALNAME));
            msg.setConversationId(ADD_PLACE_ID);
            try {
                msg.setContentObject(newPlace);
            } catch (IOException e) {
                e.printStackTrace();
            }
            send(msg);
        }
        else if (mode.equals("Host")) {
            addBehaviour(new sendSelectedPlacesBehaviour());
        }
    }

    public ArrayList<AgentPos> getPlaces() {
        return selectedPlaces;
    }

    public void addVote(final String markerName) {
        if (votesLeft > 0) {
            if (mode.equals("Host")) {
                if (!votes.containsKey(markerName))
                    votes.put(markerName, 1);
                else
                    votes.put(markerName, votes.get(markerName) + 1);

                Intent broadcast = new Intent();
                broadcast.setAction("inz.agents.MobileAgent.VOTES_UPDATED");
                context.sendBroadcast(broadcast);
            } else if (mode.equals("Client")) {
                addBehaviour(new OneShotBehaviour() {
                    @Override
                    public void action() {
                        ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);
                        msg.addReceiver(new AID(hostName, AID.ISLOCALNAME));
                        msg.setConversationId(ADD_VOTE_ID);
                        msg.setContent(markerName);
                        send(msg);
                    }
                });
            }
        }
        --votesLeft;
    }

    public HashMap<String, Integer> getVotes() { return votes; }

    public void choosePlace(String name) {
        for(int i=0; i<selectedPlaces.size(); i++) {
            if(selectedPlaces.get(i).getName().equals(name))
                destination = selectedPlaces.get(i);
        }

        addBehaviour(new sendDestinationBehaviour());

        Intent broadcast = new Intent();
        broadcast.setAction("inz.agents.MobileAgent.DEST_CHOSEN");
        context.sendBroadcast(broadcast);
    }

    public AgentPos getDestination() { return destination; }

    public int getMaxVotes() { return MAX_VOTES; }

    /***********************************************
     ***********************************************
     **************  BEHAVIOURS  *******************
     ***********************************************
     ***********************************************/

    private class deRegisterBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST), MessageTemplate.MatchConversationId(DEREGISTRATION_ID));
            ACLMessage msg = myAgent.receive(mt);
            if(msg != null) {
                for(AgentPos aGroup: group){
                    if(aGroup.getName().equals(msg.getSender().getLocalName())) {
                        group.remove(group.indexOf(aGroup));
                        addBehaviour(new UpdateGroupBehaviour());

                        Intent broadcast = new Intent();
                        broadcast.setAction("inz.agents.MobileAgent.AGENT_LEFT");
                        broadcast.putExtra("NAME", msg.getSender().getLocalName());
                        context.sendBroadcast(broadcast);

                        if(state == State.GATHER)
                            calculateCenter();
                        break;
                    }
                }
            }
            else
                block();
        }
    }

    private class registerBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST), MessageTemplate.MatchConversationId(REGISTRATION_ID));
            ACLMessage msg = myAgent.receive(mt);
            if(msg != null) {
                group.add(new AgentPos(msg.getSender().getLocalName(), null));
                addBehaviour(new UpdateGroupBehaviour());
            }
            else
                block();

        }
    }

    private class UpdateGroupBehaviour extends OneShotBehaviour {

        @Override
        public void action() {
            ACLMessage response = new ACLMessage(ACLMessage.INFORM);
            for (AgentPos aGroup : group) {
                if(!aGroup.getName().equals(this.getAgent().getLocalName()))
                    response.addReceiver(new AID(aGroup.getName(), AID.ISLOCALNAME));
            }
            response.setConversationId(GROUP_UPDATE_ID);
            try {
                response.setContentObject( group.toArray());
            } catch (IOException e) {
                e.printStackTrace();
            }
            send(response);
            Intent broadcast = new Intent();
            broadcast.setAction("inz.agents.MobileAgent.GROUP_UPDATE");
            context.sendBroadcast(broadcast);
        }
    }

    private class getSelectedPlaceBehaviour extends SimpleBehaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId(PLACES_ID));
            ACLMessage msg = myAgent.receive(mt);
            if(msg != null) {
                try {
                    Object[] c = (Object[]) msg.getContentObject();
                    selectedPlaces = new ArrayList<>();
                    for(Object obj: c) {
                        selectedPlaces.add((AgentPos) obj);
                    }

                } catch (UnreadableException e) {
                    e.printStackTrace();
                }

                Intent broadcast = new Intent();
                broadcast.setAction("inz.agents.MobileAgent.PLACES_UPDATED");
                context.sendBroadcast(broadcast);
            }
            else
                block();
        }

        @Override
        public boolean done() {
            return (state != State.CHOOSE);
        }
    }

    private class getPlaceBehaviour extends SimpleBehaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId(ADD_PLACE_ID));
            ACLMessage msg = myAgent.receive(mt);
            if(msg != null) {
                try {
                    AgentPos place =(AgentPos) msg.getContentObject();
                    selectedPlaces.add(place);

                } catch (UnreadableException e) {
                    e.printStackTrace();
                }

                addBehaviour(new sendSelectedPlacesBehaviour());
                Intent broadcast = new Intent();
                broadcast.setAction("inz.agents.MobileAgent.PLACES_UPDATED");
                context.sendBroadcast(broadcast);
            }
            else
                block();
        }

        @Override
        public boolean done() {
            return (state != State.CHOOSE);
        }
    }

    private class sendSelectedPlacesBehaviour extends OneShotBehaviour {

        @Override
        public void action() {
            ACLMessage response = new ACLMessage(ACLMessage.INFORM);
            for (AgentPos aGroup : group) {
                if(!aGroup.getName().equals(this.getAgent().getLocalName()))
                    response.addReceiver(new AID(aGroup.getName(), AID.ISLOCALNAME));
            }
            response.setConversationId(PLACES_ID);
            try {
                response.setContentObject( selectedPlaces.toArray());
            } catch (IOException e) {
                e.printStackTrace();
            }
            send(response);
        }
    }

    private class sendDestinationBehaviour extends OneShotBehaviour {

        @Override
        public void action() {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            for (AgentPos aGroup : group) {
                if(!aGroup.getName().equals(this.getAgent().getLocalName()))
                    msg.addReceiver(new AID(aGroup.getName(), AID.ISLOCALNAME));
            }
            msg.setConversationId(DESTINATION_ID);
            try {
                msg.setContentObject(destination);
            } catch (IOException e) {
                e.printStackTrace();
            }
            send(msg);
        }
    }

    private class getDestinationBehaviour extends SimpleBehaviour {

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId(DESTINATION_ID));
            ACLMessage msg = myAgent.receive(mt);
            if(msg != null) {
                try {
                   destination = (AgentPos) msg.getContentObject();

                } catch (UnreadableException e) {
                    e.printStackTrace();
                }

                Intent broadcast = new Intent();
                broadcast.setAction("inz.agents.MobileAgent.DEST_CHOSEN");
                context.sendBroadcast(broadcast);;
            }
            else
                block();
        }

        @Override
        public boolean done() {
            return (destination != null);
        }
    }

    private class getVoteBehaviour extends SimpleBehaviour {
        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE), MessageTemplate.MatchConversationId(ADD_VOTE_ID));
            ACLMessage msg = myAgent.receive(mt);
            if(msg != null) {
                String name = msg.getContent();
                if(!votes.containsKey(name))
                    votes.put(name, 1);
                else
                    votes.put(name, votes.get(name)+1);

                Intent broadcast = new Intent();
                broadcast.setAction("inz.agents.MobileAgent.VOTES_UPDATED");
                context.sendBroadcast(broadcast);
            }
            else
                block();
        }

        @Override
        public boolean done() {
            return (state != State.VOTE);
        }
    }

}
