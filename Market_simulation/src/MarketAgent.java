import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by felentovic on 29/11/17.
 * Market agent has behaviours for producing a product, selling it and ordering a required resources for production. There
 * are rules for resources which are required for the production, other agents products. Agent as an argument receives
 * probability to reject other agents proposal pRefuse , it simulates unavailability during the holidays or problems in transport
 * and etc. OrderMoreRate is parameter used when agents proposal is rejected so next time it will order more in case it gets rejected again in future. OrderMoreP [1,1.5].
 * Next paramater is valueOfProduct which says value of agents product in some currency. After that he receives budget which
 * he can spend for buying resources and rules in form (agentName,neededResources).Each iteration of ordering orderMoreP parameter
 * is reduced for orderMoreRate/3 if proposal is accepted or increased if proposal is rejected. When ordering a product,
 * amount of product is calculated as missingAmount + missingAmount*orderMoreP.
 */
public class MarketAgent extends Agent {
    public int productsNum;
    public int soFarProductNum;
    public double pRefuse;
    public long delayProduction;
    public long valueOfProduct;
    public long budget;
    public double orderMoreRate;
    public Map<String, Integer> resources;
    public Map<String, Integer> rules;
    public Map<String, Long> prices;
    public BufferedWriter writer;

    protected void setup() {
        resources = new HashMap<>();
        rules = new HashMap<>();
        prices = new HashMap<>();
        pRefuse = Double.parseDouble((String) this.getArguments()[0]);
        orderMoreRate = Double.parseDouble((String) this.getArguments()[1]);
        valueOfProduct = Long.parseLong((String) this.getArguments()[2]);
        budget = Long.parseLong((String) this.getArguments()[3]);
        for (int i = 4, stop = this.getArguments().length; i < stop; i += 2) {
            rules.put((String) this.getArguments()[i], Integer.parseInt((String) this.getArguments()[i + 1]));
        }

//        try {
//            Thread.sleep(10000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        delayProduction = valueOfProduct * 1;
//        try {
//            writer = Files.newBufferedWriter(Paths.get(this.getLocalName()+".txt"));
//            writer.write("values\n");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        addBehaviours();
    }

    @Override
    protected void takeDown() {
        try {
            writer.write(String.valueOf(this.budget)+"\n");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addBehaviours() {
        addBehaviour(new ProduceAgent(this));
        addBehaviour(new SellAgent(this));
        for (String agentVendor : rules.keySet()) {
            addBehaviour(new BuyMarketAgent(this, agentVendor));
        }
//        addBehaviour(new WriteInFileAgent(this));
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
            if (rule.getValue() > resources.getOrDefault(rule.getKey(), 0)) {
                enough = false;
                break;
            }
        }
        return enough;
    }

    public long calculateAssetsValue() {
        long assetsVal = 0;
        for (Map.Entry<String, Integer> rule : rules.entrySet()) {
            assetsVal += resources.getOrDefault(rule.getKey(), 0) * this.prices.get(rule.getKey());
        }
        return assetsVal;
    }
}

abstract class MarketAgentsBehaviour extends CyclicBehaviour {
    protected MarketAgent agent;

    public MarketAgentsBehaviour(MarketAgent a) {
        this.agent = a;
    }
}


class WriteInFileAgent extends MarketAgentsBehaviour{
    private long prev = 0;

    public WriteInFileAgent(MarketAgent a) {
        super(a);
    }

    @Override
    public void action() {

        long now =System.currentTimeMillis();
        if (prev == 0 || now  - prev > 100) {
            try {
                agent.writer.write(String.valueOf(agent.budget) + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
            prev = now;
        }
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
            agent.productsNum++;
            agent.soFarProductNum++;
            System.out.println(agent.getLocalName() + ": I created a product. So far created " + agent.soFarProductNum + ". " +
                    "Now I have " + agent.productsNum + " products. Cash: " + agent.budget);
//            try {
//                Thread.sleep(agent.delayProduction);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            block(agent.delayProduction);
        }
    }
}//end ProduceAgent

class SellAgent extends MarketAgentsBehaviour {
    private MessageTemplate mt;

    public SellAgent(MarketAgent a) {
        super(a);
        mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE), MessageTemplate.MatchPerformative(ACLMessage.INFORM_IF));
    }

    @Override
    public void action() {

        ACLMessage offer = agent.receive(mt);
        if (offer != null) {
            if (offer.getPerformative() == ACLMessage.PROPOSE) {
                //wants to buy products
                String text = offer.getSender().getLocalName() + " asked me -" + agent.getLocalName() + " for " + offer.getContent() + " products. ";
                int neededResources = Integer.parseInt(offer.getContent());
                ACLMessage reply = offer.createReply();
                int soldProducts = Math.min(agent.productsNum, neededResources);
                if (Math.random() > agent.pRefuse && soldProducts > 0) {
                    //accept offer
                    agent.productsNum -= soldProducts;
                    agent.budget += soldProducts * agent.valueOfProduct;
                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                    text += "Products PROPOSAL_ACCEPTED. Selling " + soldProducts + " products.";
                    //System.out.println("I sold something. " + agent.getLocalName() + " now I have: " + agent.budget);

                } else {
                    //refuse offer
                    reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                    soldProducts = 0;
                    text += "Products PROPOSAL_REJECTED";

                }
                System.out.println(text);
                System.out.flush();
                reply.setContent(String.valueOf(soldProducts));
                agent.send(reply);
            } else if (offer.getPerformative() == ACLMessage.INFORM_IF) {
                //asked about my price
                ACLMessage reply = offer.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                System.out.println(offer.getSender().getLocalName() + " asked me -" + agent.getLocalName() + " about the price of my product");
                reply.setContent(String.valueOf(agent.valueOfProduct));
                agent.send(reply);
            } else {
                System.out.println("ERROR");
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
    private Integer delayBuying;
    private double orderMoreP;

    public BuyMarketAgent(MarketAgent a, String buyFrom) {
        super(a);
        this.orderMoreP = 1;
        this.buyFrom = buyFrom;
        this.conversationId = "trade:" + agent.getLocalName() + "-" + buyFrom;
        this.messageTemplate = MessageTemplate.MatchConversationId(conversationId);
        delayBuying = 1;
        step = 0;
    }

    @Override
    public void action() {

        if (step == 0 && agent.prices.containsKey(buyFrom)) {
            step = 2;
        }
        switch (step) {
            case 0:
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM_IF);
                msg.addReceiver(new AID(buyFrom, AID.ISLOCALNAME));
                msg.setConversationId(conversationId);
                agent.send(msg);
                step++;
                break;
            case 1:
                ACLMessage priceReply = agent.receive(messageTemplate);
                if (priceReply != null) {
                    if (priceReply.getPerformative() == ACLMessage.INFORM) {
                        long price = Long.parseLong(priceReply.getContent());
                        agent.prices.put(buyFrom, price);
                        step++;
                    }

                } else {
                    block();
                }
                break;
            case 2:
                //make an order
                Integer missingAmount = Math.max(agent.rules.get(buyFrom) - agent.resources.getOrDefault(buyFrom, 0), 0);
                if(missingAmount == 0){
                    return;
                }
                Long willOrder = Math.min(Math.round(missingAmount * orderMoreP), (int) (agent.budget / (missingAmount*agent.prices.get(buyFrom))));
                if(willOrder == 0){
                    return;
                }
                System.out.println(agent.getLocalName() + " ordering " + willOrder + " amount of product " + buyFrom + ".orderMoreP "
                        + orderMoreP + ". I really need " + missingAmount);
                msg = new ACLMessage(ACLMessage.PROPOSE);
                msg.addReceiver(new AID(buyFrom, AID.ISLOCALNAME));
                msg.setConversationId(conversationId);
                msg.setContent(willOrder.toString());
                agent.send(msg);
                step++;
                break;
            case 3:
                ACLMessage reply = agent.receive(messageTemplate);
                if (reply != null) {
                    if (reply.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                        int boughtAmount = Integer.parseInt(reply.getContent());
                        int currentAmount = agent.resources.getOrDefault(buyFrom, 0);
                        agent.resources.put(buyFrom, currentAmount + boughtAmount);
                        agent.budget -= boughtAmount * agent.prices.get(buyFrom);
                        orderMoreP = Math.max(orderMoreP - (agent.orderMoreRate * orderMoreP) / 3, 1);
                        delayBuying = 1;
                        step = 0;
                        System.out.println("I "+agent.getLocalName()+" bought from " + buyFrom + " "+boughtAmount+" products." +
                                " now I have: " + agent.budget);
                    } else if (reply.getPerformative() == ACLMessage.REJECT_PROPOSAL) {
                        //cryyy :(
                        //double order at most
                        orderMoreP = Math.min(orderMoreP + (agent.orderMoreRate * orderMoreP), 1.5);
                        //delayBuying *= 2;
                        step = 0;
                        block(delayBuying);
                    } else {
                        System.out.println("ERROR!");
                    }
                } else {
                    block();
                }
                break;

        }

    }

}//end BuyMarketAgent