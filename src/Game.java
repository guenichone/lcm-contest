import java.util.*;

class Player {

    private static final Channel LOG = Channel.getInstance();

    private static int roundNumber = 1;

    public static void main(String args[]) {

        Channel.DEBUG_ENABLED = false;

        Scanner in = new Scanner(System.in);

        LOG.debug("Game start ...");

        Engine engine = new EfficiencyDraftEngine();

        while (true) {

            Round round = RoundParser.parseRound(in);
            LOG.info("Round status : %s", round);

            engine.processRound(round);

            if (roundNumber == 30) {
                LOG.info("Round 30 switching to game engine");
                engine = new GameEngine(round.cardList);
            }

            LOG.debug("End of turn " + roundNumber);

            roundNumber++;
        }
    }
}

class EfficiencyDraftEngine implements Engine {

    private static final Channel CHANNEL = Channel.getInstance();

    @Override
    public void processRound(Round round) {
        Card selectedCard = null;
        float maxEfficiency = Float.MAX_VALUE;
        int selectedIdx = -1;
        int idx = 0;
        for (Card card : round.cardList) {
            float costEfficiency = costEfficiency(card);
            if (costEfficiency < maxEfficiency) {
                selectedCard = card;
                maxEfficiency = costEfficiency;
                selectedIdx = idx;
            }
            idx++;
        }

        CHANNEL.info("Picking card %s at idx %d", selectedCard.toString(), selectedIdx);
        pickCard(selectedIdx);
    }

    public float costEfficiency(Card card) {
        if (!card.hasHabilities()) {
            return (card.attack + card.defense) / card.cost;
        } else {
            return 1;
        }
    }

    private static void pickCard(int number) {
        System.out.println("PICK " + number);
    }
}

class GameEngine implements Engine {

    private static final Channel CHANNEL = Channel.getInstance();

    private List<Card> deck;

    private List<Card> hand;

    private List<Card> board = new ArrayList<>();
    private List<Card> opponentBoard = new ArrayList<>();

    private List<Card> graveyard = new ArrayList<>();
    private List<Card> opponentGraveyard = new ArrayList<>();

    public GameEngine(List<Card> deck) {
        this.deck = deck;
    }

    @Override
    public void processRound(Round round) {
        this.hand = round.cardList;
        this.hand.removeAll(board);

        CHANNEL.info("Hand : %s", hand.toString());
        CHANNEL.info("Board : %s", board.toString());

        List<Action> actionList = new ArrayList<>();

        // Invoke
        actionList.addAll(bestSummonAction(round));

        // Attack
        actionList.addAll(bestAttackAction(round));

        play(actionList);
    }

    private void play(List<Action> actionList) {
        if (actionList.isEmpty()) {
            actionList.add(new Pass());
        }

        CHANNEL.play(actionList);
    }

    private List<Action> bestSummonAction(Round round) {
        List<Action> actionList = new ArrayList<>();
        int spentMana = 0;

        // Remove already played cards
        round.cardList.removeAll(board);
        // Sort decreasing cost order
        round.cardList.sort(Comparator.comparingInt((Card o) -> o.cost).reversed());

        for (Card card : round.cardList) {
            if (card.cost < round.current.playerMana - spentMana) {
                spentMana += card.cost;
                board.add(card);
                actionList.add(new Summon(card.instanceId));
            }
        }
        return actionList;
    }

    private List<Action> bestAttackAction(Round round) {
        List<Action> actionList = new ArrayList<>();
        // Banzai !
        for (Card card : board) {
            actionList.add(new Attack(card.instanceId));
        }
        return actionList;
    }

}

interface Engine {
    void processRound(Round round);
}

class Round {
    public PlayerStatus current;
    public PlayerStatus opponent;

    public int opponentHand;
    public int cardCount;

    public List<Card> cardList;

    public Round(PlayerStatus current, PlayerStatus opponent, int opponentHand, int cardCount, List<Card> cardList) {
        this.current = current;
        this.opponent = opponent;
        this.opponentHand = opponentHand;
        this.cardCount = cardCount;
        this.cardList = cardList;
    }

    @Override
    public String toString() {
        return "\n- Me:" + current +
                "\n- Opponent=" + opponent +
                "\n- opponentHand=" + opponentHand +
                "\n- cardCount=" + cardCount +
                "\n- cardList=" + cardList;
    }
}

class PlayerStatus {
    public int playerHealth;
    public int playerMana;
    public int playerDeck;
    public int playerRune;

    public PlayerStatus(Scanner in) {
        this.playerHealth = in.nextInt();
        this.playerMana = in.nextInt();
        this.playerDeck = in.nextInt();
        this.playerRune = in.nextInt();
    }

    @Override
    public String toString() {
        return "health=" + playerHealth +
                ", mana=" + playerMana +
                ", deck=" + playerDeck +
                ", rune=" + playerRune;
    }
}

class Card {

    private static final String EMPTY_HABILITIES = "------";

    public int cardNumber;
    public int instanceId;
    public int location;

    public int cardType;
    public int cost;
    public int attack;
    public int defense;

    public String abilities;

    public int myHealthChange;
    public int opponentHealthChange;
    public int cardDraw;

    public Card(Scanner in) {
        this.cardNumber = in.nextInt();
        this.instanceId = in.nextInt();
        this.location = in.nextInt();

        this.cardType = in.nextInt();
        this.cost = in.nextInt();
        this.attack = in.nextInt();
        this.defense = in.nextInt();

        this.abilities = in.next();

        this.myHealthChange = in.nextInt();
        this.opponentHealthChange = in.nextInt();
        this.cardDraw = in.nextInt();
    }

    @Override
    public String toString() {
        String result = simpleString();
        if (hasHabilities()) {
            result +="("
                    + (!EMPTY_HABILITIES.equals(abilities) ? abilities : "")
                    + (myHealthChange != 0 ? "MH" + myHealthChange : "")
                    + (opponentHealthChange != 0 ? "OH" + opponentHealthChange : "")
                    + (cardDraw != 0 ? "D" + cardDraw : "")
                    + ")";
        }
        return result;
    }

    public String simpleString() {
        return String.format("[id%d; M%d, A%d, D%d]", instanceId, cost, attack, defense);
    }

    public boolean hasHabilities() {
        return !EMPTY_HABILITIES.equals(abilities) || myHealthChange != 0 || opponentHealthChange != 0 || cardDraw != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return instanceId == card.instanceId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceId);
    }
}

class RoundParser {

    private static final Channel LOG = Channel.getInstance();

    public static Round parseRound(Scanner in) {

        PlayerStatus current = new PlayerStatus(in);
        PlayerStatus opponent = new PlayerStatus(in);

        int opponentHand = in.nextInt();
        int cardCount = in.nextInt();

        List<Card> cardList = new ArrayList<>();
        for (int i = 0; i < cardCount; i++) {
            Card card = new Card(in);
            LOG.debug("Card %s", card.toString());
            cardList.add(card);
        }

        return new Round(current, opponent, opponentHand, cardCount, cardList);
    }
}

class Channel {

    private static Channel INSTANCE = new Channel();
    public static boolean DEBUG_ENABLED = true;

    public static Channel getInstance() {
        return INSTANCE;
    }

    public void info(String message, Object... args) {
        System.err.println("Info: " + String.format(message, args));
    }

    public void info(String message, List<Object> args) {
        System.err.println("Info: " + String.format(message, args.toString()));
    }

    public void debug(String message, Object... args) {
        if (DEBUG_ENABLED) {
            System.err.println("Debug: " + String.format(message, args));
        }
    }

    public void debug(String message, List<Object> args) {
        if (DEBUG_ENABLED) {
            System.err.println("Debug: " + String.format(message, args));
        }
    }

    public void play(Action... actions) {
        info("Play actions : " + join(actions));
        System.out.println(join(actions));
    }

    public void play(List<Action> actions) {
        this.play(actions.toArray(new Action[actions.size()]));
    }

    private String join(Action... actions) {
        if (actions.length == 1) {
            return actions[0].toString();
        } else {
            StringBuilder builder = new StringBuilder();
            for (Action action : actions) {
                builder.append(action.toString()).append(";");
            }
            return builder.toString();
        }
    }
}

abstract class Action {
    private String action;

    public Action(String action) {
        this.action = action;
    }

    @Override
    public String toString() {
        return action;
    }
}

class Pass extends Action {
    public Pass() {
        super("PASS");
    }
}

class Pick extends Action {
    public Pick(int cardNumber) {
        super("PICK " + cardNumber);
    }
}

class Summon extends Action {
    public Summon(int id) {
        super("SUMMON " + id);
    }
}

class Attack extends Action {
    public Attack(int id) {
        super("ATTACK " + id + " -1");
    }

    public Attack(int id, int targetId) {
        super("ATTACK " + id + " " + targetId);
    }
}
