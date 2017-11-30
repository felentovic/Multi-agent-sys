import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by felentovic on 29/11/17.
 * Market agent has methods for producing a product, selling it and ordering a required resources for production. There
 * are rules for resources which are required for the production, other agents products. Agent as an argument receives
 * probability to reject other agents proposal pRefuse , it simulates unavailability during the holidays or problems in transport
 * and etc. There is parameter delayProduction which simulates time needed for producing a product. OrderMoreRate is parameter
 * used when agents proposal is rejected so next time it will order more in case it gets rejected again in future. Each
 * iteration of ordering orderMoreP parameter is reduced for orderMoreRate/3 if proposal is accepted or increased if
 * proposal is rejected. When ordering a product, amount of product is calculated as missingAmount + missingAmount*orderMoreP.
 */
public class MarketAgent extends Agent {
    public int productsNum;
    public double pRefuse;
    public int delayProduction;
    public double orderMoreRate;
    public double orderMoreP;
    public Map<String, Integer> resources;
    public Map<String, Integer> rules;

    protected void setup() {
        resources = new HashMap<>();
        rules = new HashMap<>();
        pRefuse = (Double) this.getArguments()[0];
        delayProduction = (Integer) this.getArguments()[1];
        orderMoreRate = (Double) this.getArguments()[2];
        for (int i = 0; i < (this.getArguments().length - 3) / 2; i++) {
                rules.put((String)this.getArguments()[i], (Integer) this.getArguments()[i+1]);
        }

        addBehaviours();
    }

    private void addBehaviours() {
        addBehaviour(new ProduceAgent(this));
        addBehaviour(new SellAgent(this));
        for (String agentVendor : rules.keySet()) {
            addBehaviour(new BuyMarketAgent(this, agentVendor));
        }
    }


    public void createProduct() {
        for (Map.Entry<String, Integer> rule : rules.entrySet()) {
            int newVal = resources.get(rule.getKey()) - rule.getValue();
            resources.put(rule.getKey(), newVal);

        }
    }

    public boolean enoughResources() {
        boolean enough = true;
        for (Map.Entry<String, Integer> rule : rules.entrySet()) {
            if (rule.getValue() > resources.get(rule.getKey())) {
                enough = false;
                break;
            }
        }
        return enough;
    }
}

abstract class MarketAgentsBehaviour extends CyclicBehaviour {
    protected MarketAgent agent;

    public MarketAgentsBehaviour(MarketAgent a) {
        super(a);
        this.agent = a;
    }
}

class ProduceAgent extends MarketAgentsBehaviour {

    public ProduceAgent(MarketAgent a) {
        super(a);
    }

    /**
     * produce a product
     */
    @Override
    public void action() {
        if (agent.enoughResources()) {
            agent.createProduct();
        }
        agent.productsNum++;
        block(agent.delayProduction);
    }
}//end ProduceAgent

class SellAgent extends MarketAgentsBehaviour {

    public SellAgent(MarketAgent a) {
        super(a);
    }

    @Override
    public void action() {
        ACLMessage offer = agent.receive();
        if (offer != null) {
            // Reply received
            if (offer.getPerformative() == ACLMessage.PROPOSE) {
                String text = agent.getLocalName() + " asked " + agent.getLocalName() + " for " + offer.getContent() + " products. ";
                int neededResources = Integer.parseInt(offer.getContent());
                ACLMessage reply = offer.createReply();
                int soldProducts = Math.max(0, agent.productsNum - neededResources);
                if (Math.random() > agent.pRefuse && soldProducts > 0) {
                    //accept offer
                    agent.productsNum -= soldProducts;
                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                    text += "Products PROPOSAL_ACCEPTED. Selling " + soldProducts + " products.";
                } else {
                    //refuse offer
                    reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                    soldProducts = 0;
                    text += "Products PROPOSAL_REJECTED";

                }
                System.out.println(text);
                reply.setContent(String.valueOf(soldProducts));
                agent.send(reply);
            }
        } else {
            block();
        }
    }

}//end SellAgent


class BuyMarketAgent extends MarketAgentsBehaviour {
    private String buyFrom;
    private int step;
    private String conversationId;
    private MessageTemplate messageTemplate;

    public BuyMarketAgent(MarketAgent a, String buyFrom) {
        super(a);
        this.buyFrom = buyFrom;
        this.conversationId = "trade:" + agent.getName() + "-" + buyFrom;
        this.messageTemplate = MessageTemplate.MatchConversationId(conversationId);
    }

    @Override
    public void action() {

        if (step == 0) {
            //make an order
            Integer missingAmount = Math.max(agent.rules.get(buyFrom) - agent.resources.get(buyFrom), 0);
            Long willOrder = Math.round(missingAmount + missingAmount * agent.orderMoreP);
            System.out.println(agent.getName()+" ordering "+willOrder+" amount of product "+buyFrom+",although I need "+missingAmount);
            ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);
            msg.addReceiver(new AID(buyFrom, AID.ISLOCALNAME));
            msg.setConversationId(conversationId);
            msg.setContent(willOrder.toString());
            agent.send(msg);
            step++;
        } else {
            ACLMessage reply = agent.receive(messageTemplate);
            if (reply != null) {
                if (reply.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                    int buyedAmout = Integer.parseInt(reply.getContent());
                    int currentAmount = agent.resources.get(buyFrom);
                    agent.resources.put(buyFrom, currentAmount + buyedAmout);
                    agent.orderMoreP = agent.orderMoreP - (agent.orderMoreRate * agent.orderMoreP) / 5;
                } else if (reply.getPerformative() == ACLMessage.REJECT_PROPOSAL) {
                    //cryyy :((
                    //
                    agent.orderMoreP = agent.orderMoreP + (agent.orderMoreRate * agent.orderMoreP);

                } else {
                    System.out.println("ERROR!");
                }

                step = 0;
            } else {
                block();
            }
        }
    }

}//end BuyMarketAgent