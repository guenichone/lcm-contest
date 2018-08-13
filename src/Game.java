import java.util.*;

class Player {

    private static final Logger LOG = Logger.getInstance();

    private static int roundNumber = 1;

    public static void main(String args[]) {

        Scanner in = new Scanner(System.in);

        LOG.debug("Game start ...");

        while (true) {

            Round round = RoundParser.parseRound(in);
            LOG.info("Round status : %s", round);

            if (roundNumber <= 30) {
                DraftEngine.processRound(round);
            } else {
                GameEngine.processRound(round);
            }

            LOG.debug("End of turn " + roundNumber);
            roundNumber++;
        }
    }
}

class DraftEngine {

    private static final Logger LOG = Logger.getInstance();

    public static void processRound(Round round) {
        Card selectedCard = null;
        float maxEfficiency = Float.MAX_VALUE;
        int selectedIdx = -1;
        int idx = 0;
        for (Card card : round.cardList) {
            float costEfficiency = card.costEfficiency();
            if (costEfficiency < maxEfficiency) {
                selectedCard = card;
                maxEfficiency = costEfficiency;
                selectedIdx = idx;
            }
            idx++;
        }

        LOG.info("Picking card %s at idx %d", selectedCard.toString(), selectedIdx);
        pickCard(selectedIdx);
    }

    private static void pass() {
        System.out.println("PASS");
    }

    private static void pickCard(int number) {
        System.out.println("PICK " + number);
    }
}

class GameEngine {
    public static void processRound(Round round) {

    }
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

    public float costEfficiency() {
        if (!hasHabilities()) {
            return (attack + defense) / cost;
        } else {
            return 1;
        }
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
        return String.format("[M%d,A%d,D%d]", cost, attack, defense);
    }

    public boolean hasHabilities() {
        return !EMPTY_HABILITIES.equals(abilities) || myHealthChange != 0 || opponentHealthChange != 0 || cardDraw != 0;
    }
}

class RoundParser {

    private static final Logger LOG = Logger.getInstance();

    public static Round parseRound(Scanner in) {

        PlayerStatus current = new PlayerStatus(in);
        PlayerStatus opponent = new PlayerStatus(in);

//        LOG.debug("Current player : " + current);
//        LOG.debug("Opponent player : " + current);

        int opponentHand = in.nextInt();
        int cardCount = in.nextInt();

//        LOG.debug("Opponent hand " + opponentHand);
//        LOG.debug("Card count " + cardCount);

        List<Card> cardList = new ArrayList<>();
        for (int i = 0; i < cardCount; i++) {
            Card card = new Card(in);
            LOG.info("Card %s", card.toString());
            cardList.add(card);
        }

        return new Round(current, opponent, opponentHand, cardCount, cardList);
    }
}

class Logger {

    private static Logger INSTANCE = new Logger();
    private static boolean debugEnabled = true;

    public static Logger getInstance() {
        return INSTANCE;
    }

    public void info(String message, Object... args) {
        System.err.println(String.format(message, args));
    }

    public void debug(String message, Object... args) {
        if (debugEnabled) {
            System.err.println(String.format(message, args));
        }
    }
}