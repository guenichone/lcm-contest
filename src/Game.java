import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

class Player {

    private static final Channel LOG = Channel.getInstance();

    private static int roundNumber = 1;

    public static void main(String args[]) {

        Channel.DEBUG_ENABLED = false;

        Scanner in = new Scanner(System.in);

        LOG.info("Game start ...");

        Engine engine = new EfficiencyDraftEngine();

        while (true) {

            Round round = RoundParser.parseRound(in);
            LOG.info("Round status : %s", round);

            engine.processRound(round);

            if (roundNumber == 30) {
                LOG.info("Round 30 switching to game engine");
                engine = new GameEngine(((EfficiencyDraftEngine) engine).deck);
            }

            LOG.info("End of turn " + roundNumber);

            roundNumber++;
        }
    }
}

class EfficiencyDraftEngine implements Engine {

    private static final Channel CHANNEL = Channel.getInstance();

    public List<Card> deck = new ArrayList<>();

    @Override
    public void processRound(Round round) {
        Card selectedCard = null;
        float maxEfficiency = Float.MAX_VALUE;
        int selectedIdx = -1;
        int idx = 0;
        for (Card card : round.hand) {
            float costEfficiency = costEfficiency(card);
            if (costEfficiency < maxEfficiency) {
                selectedCard = card;
                maxEfficiency = costEfficiency;
                selectedIdx = idx;
            }
            idx++;
        }

        CHANNEL.info("Picking card %s at idx %d", selectedCard.toString(), selectedIdx);
        deck.add(selectedCard);
        CHANNEL.play(new Pick(selectedIdx));
    }

    public float costEfficiency(Card card) {
        if (!card.hasHabilities()) {
            return (card.attack + card.defense) / card.cost;
        } else {
            return 1;
        }
    }
}

class GameEngine implements Engine {

    private static final Channel CHANNEL = Channel.getInstance();

    private List<Card> deck;
    private List<Card> summoningSickness = new ArrayList<>();

    public GameEngine(List<Card> deck) {
        this.deck = deck;
    }

    @Override
    public void processRound(Round round) {
        summoningSickness.clear();
        deck.removeAll(round.hand);

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

        // Sort decreasing cost order
        round.hand.sort(Comparator.comparingInt((Card o) -> o.cost).reversed());

        for (Card card : round.hand) {
            if (card.cost < round.current.playerMana - spentMana) {
                spentMana += card.cost;
                if (!card.hasCharge()) {
                    summoningSickness.add(card);
                }
                actionList.add(new Summon(card.instanceId));
            }
        }
        return actionList;
    }

    private List<Action> bestAttackAction(Round round) {
        List<Action> actionList = new ArrayList<>();

        // Remove invoked creatures from board
        round.board.removeAll(summoningSickness);

        // Get defensers
        List<Card> defensers = getDefensers(round.opponentBoard);

        if (defensers.isEmpty()) {
            CHANNEL.info("No defensers ... banzai");
            // Banzai !
            for (Card card : round.board) {
                actionList.add(new Attack(card.instanceId));
            }
        } else {
            CHANNEL.info("Found defensers : %s", defensers.toString());
            Map<Card, Integer> lifeMap = defensers.stream()
                    .collect(Collectors.toMap(Function.identity(), Card::getDefense));

            // Focus defensers
            for (Card card : round.board) {
                Card target = findBestTarget(card, lifeMap);
                if (target != null) {
                    actionList.add(new Attack(card.instanceId, target.instanceId));
                } else {
                    actionList.add(new Attack(card.instanceId));
                }
            }
        }
        return actionList;
    }

    private Card findBestTarget(Card attacker, Map<Card, Integer> lifeMap) {
        int maxDamage = 0;
        Card target = null;
        for (Map.Entry<Card, Integer> entry : lifeMap.entrySet()) {
            if (entry.getValue() > 0 // Not dead yet
                    && attacker.attack <= entry.getValue() // Deal no more damage
                    && attacker.attack > maxDamage) { // Do more damage than previous one

                maxDamage = attacker.attack;
                target = entry.getKey();
                entry.setValue(entry.getValue() - attacker.attack);
            }
        }

        if (target == null) { // Maybe there will be no more defenser OR attack is greater than defense
            for (Map.Entry<Card, Integer> entry : lifeMap.entrySet()) {
                if (entry.getValue() > 0) {
                    entry.setValue(entry.getValue() - attacker.attack);
                    return entry.getKey();
                }
            }
        }

        return target;
    }

    private List<Card> getDefensers(List<Card> board) {
        return board.stream().filter(Card::hasGuard).collect(Collectors.toList());
    }

    private List<Card> getBreakthrough(List<Card> board) {
        return board.stream().filter(Card::hasBreakthrough).collect(Collectors.toList());
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

    public List<Card> hand = new ArrayList<>();
    public List<Card> board = new ArrayList<>();
    public List<Card> opponentBoard = new ArrayList<>();

    public Round(PlayerStatus current, PlayerStatus opponent, int opponentHand, int cardCount, List<Card> cardList) {
        this.current = current;
        this.opponent = opponent;
        this.opponentHand = opponentHand;
        this.cardCount = cardCount;
        for (Card card : cardList) {
            if (card.location == -1) {
                opponentBoard.add(card);
            } else if (card.location == 0) {
                hand.add(card);
            } else {
                board.add(card);
            }
        }
    }

    @Override
    public String toString() {
        return "\n- Me:" + current +
                "\n- Opponent=" + opponent +
                "\n- opponentHand=" + opponentHand +
                "\n- cardCount=" + cardCount +
                "\n- hand=" + hand +
                "\n- board=" + board +
                "\n- opponentBoard=" + opponentBoard;
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
    public boolean breakthrough = false;
    public boolean guard = false;
    public boolean charge = false;

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
        parseAbilities(this.abilities);

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

    private void parseAbilities(String abilities) {
        this.breakthrough = abilities.charAt(0) == 'B';
        this.charge = abilities.charAt(1) == 'C';
        this.guard = abilities.charAt(3) == 'G';
    }

    public String simpleString() {
        return String.format("[id%d; M%d, A%d, D%d]", instanceId, cost, attack, defense);
    }

    public boolean hasHabilities() {
        return !EMPTY_HABILITIES.equals(abilities) || myHealthChange != 0 || opponentHealthChange != 0 || cardDraw != 0;
    }

    public boolean hasGuard() {
        return guard;
    }

    public boolean hasCharge() {
        return charge;
    }

    public boolean hasBreakthrough() {
        return breakthrough;
    }

    public int getDefense() {
        return defense;
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
