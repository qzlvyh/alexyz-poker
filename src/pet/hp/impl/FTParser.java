
package pet.hp.impl;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import pet.eq.ArrayUtil;
import pet.hp.*;

public class FTParser extends Parser2 {
	
	/**
	 * @param args
	 */
	public static void main (final String[] args) throws Exception {
		final Parser parser = new FTParser();
		try (FileInputStream fis = new FileInputStream("ft.txt")) {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"))) {
				String line;
				while ((line = br.readLine()) != null) {
					final boolean hand = parser.parseLine(line);
				}
			}
		}
	}
	
	
	
	/** current line */
	private String line;
	
	public FTParser() {
		// XXX shouldnt be in constructor
		super(new History());
		debug = true;
	}
	
	@Override
	public boolean isHistoryFile (final String name) {
		return name.startsWith("FT") && name.endsWith(".txt");
	}
	
	@Override
	public boolean parseLine (final String line0) {
		line = line0.replaceAll("(\\d),(\\d)", "$1$2").replaceAll("  +", " ").trim();
		println(">>> " + line);
		
		String name;
		String buttonExp = "The button is in seat #";
		
		if (line.length() == 0) {
			if (summaryPhase && hand != null) {
				finish();
				return true;
			}
			
		} else if (line.startsWith("Full Tilt Poker Game")) {
			parseHand();
			
		} else if (line.startsWith("Seat ")) {
			println("seat");
			parseSeat();
			
		} else if (line.startsWith("Total pot ")) {
			parseTotal();
			
		} else if (line.startsWith("Board: ")) {
			parseBoard();
			
		} else if (line.startsWith("*** ")) {
			println("phase");
			parsePhase();
			
		} else if (line.startsWith("Dealt to ")) {
			println("dealt");
			parseDeal();
			
		} else if (line.startsWith(buttonExp)) {
			println("button");
			int but = Integer.parseInt(line.substring(buttonExp.length()));
			hand.button = (byte) but;
			
		} else if (line.endsWith(" has 15 seconds left to act")) {
			println("15 secs");
			
		} else if ((name = ParseUtil.parseName(seatsMap, line, 0)) != null) {
			parseAction(name);
			
		} else if (line.endsWith(" sits down")) {
			println("sit down");
			assert_(seatsMap.size() + 1 <= hand.game.max, "sit down seats < max");
			
		} else {
			fail("unmatched line");
		}
		
		return false;
	}
	
	private void parseBoard () {
		// Board: [2d 7s Th 8h 7c]
		int braIndex = line.indexOf("[");
		hand.board = ParseUtil.checkCards(hand.board, ParseUtil.parseCards(line, braIndex));
		println("board " + Arrays.asList(hand.board));
	}

	private void parseTotal () {
		// Total pot 2535 | Rake 0
		String potExp = "Total pot ";
		hand.pot = ParseUtil.parseMoney(line, potExp.length());
		int a = line.indexOf("Rake", potExp.length());
		hand.rake = ParseUtil.parseMoney(line, a + 5);
		println("total " + hand.pot + " rake " + hand.rake);
	}
	
	private void parseDeal () {
		// omaha:
		// Dealt to Keynell [Tc As Qd 3s]
		// stud:
		// Dealt to mamie2k [4d]
		// Dealt to doubleupnow [3h]
		// Dealt to bcs75 [5d]
		// Dealt to mymommy [Jh]
		// Dealt to Keynell [Qs 3s] [5s]
		// after draw: [kept] [received]
		// Dealt to Keynell [2h 4c] [Qs Kd Kh]
		
		// get seat
		// have to skip over name which could be anything
		String prefix = "Dealt to ";
		String name = ParseUtil.parseName(seatsMap, line, prefix.length());
		int cardsStart = line.indexOf("[", prefix.length() + name.length());
		Seat theseat = seatsMap.get(name);
		
		// get cards and cards 2
		String[] cards = ParseUtil.parseCards(line, cardsStart);
		int cardsStart2 = line.indexOf("[", cardsStart + 1);
		if (cardsStart2 > 0) {
			cards = ArrayUtil.join(cards, ParseUtil.parseCards(line, cardsStart2));
		}
		println(name + " dealt " + Arrays.asList(cards));
		
		// get current player seat - always has more than 1 initial hole card
		if (hand.myseat == null && cards.length > 1) {
			println("this is my seat");
			hand.myseat = theseat;
		}
		
		if (theseat == hand.myseat) {
			switch (hand.game.type) {
				case FCD:
				case DSSD:
				case DSTD:
					// hole cards can be changed in draw so store them all on
					// hand
					hand.addMyDrawCards(cards);
				default:
			}
			theseat.finalHoleCards = ParseUtil.checkCards(theseat.finalHoleCards,
					ParseUtil.getHoleCards(hand.game.type, cards));
			theseat.finalUpCards = ParseUtil.checkCards(theseat.finalUpCards,
					ParseUtil.getUpCards(hand.game.type, cards));
			
		} else {
			// not us, all cards are up cards
			theseat.finalUpCards = ParseUtil.checkCards(theseat.finalUpCards, cards);
		}
		
	}
	
	private void parsePhase () {
		// *** HOLE CARDS *** (not a street)
		// *** FLOP *** [4d 7c 2c] (Total Pot: 120, 4 Players)
		// *** TURN *** [4d 7c 2c] [Kd] (Total Pot: 240, 4 Players)
		// *** RIVER *** [4d 7c 2c Kd] [Ah] (Total Pot: 300, 2 Players)
		// *** SHOW DOWN *** (not a street)
		// *** SUMMARY *** (not a street)
		
		String t = "*** ";
		int a = line.indexOf(t);
		int b = line.indexOf(" ***", a + t.length());
		String name = line.substring(a + t.length(), b);
		boolean newStreet = false;
		boolean ignoreStreet = false;
		
		switch (hand.game.type) {
			case HE:
			case OM:
				if (name.equals("FLOP") || name.equals("TURN") || name.equals("RIVER")) {
					newStreet = true;
				} else if (name.equals("HOLE CARDS") || name.equals("PRE-FLOP")) {
					ignoreStreet = true;
				}
				break;
			default:
				fail("unf");
		}
		
		if (newStreet) {
			pip();
			newStreet();
			println("new street index " + currentStreetIndex());
			
		} else if (name.equals("SHOW DOWN")) {
			println("showdown");
			
		} else if (name.equals("SUMMARY")) {
			println("summary");
			// pip in case there is only one street
			pip();
			summaryPhase = true;
			
		} else if (!ignoreStreet) {
			throw new RuntimeException("unknown phase " + name);
		}
	}
	
	private void parseAction (String name) {
		// Keynell antes 100
		println("action: name=" + name);
		Seat seat = seatsMap.get(name);
		
		int actIndex = name.length() + 1;
		int actEndIndex = line.indexOf(" ", actIndex);
		if (actEndIndex == -1) {
			actEndIndex = line.length();
		}
		String actStr = line.substring(actIndex, actEndIndex);
		println("actStr=" + actStr);
		
		Action action = new Action(seat);
		action.type = ParseUtil.getAction(actStr);
		
		switch (action.type) {
			case ANTE: {
				int amount = ParseUtil.parseMoney(line, actEndIndex + 1);
				assert_(amount < hand.sb, "ante < sb");
				hand.antes += amount;
				post(amount);
				break;
			}
			
			case POST: {
				String sbExp = "posts the small blind of ";
				String bbExp = "posts the big blind of ";
				if (line.startsWith(sbExp, actIndex)) {
					action.amount = ParseUtil.parseMoney(line, actIndex + sbExp.length());
					seat.smallblind = true;
					assert_(action.amount == hand.sb, "post sb = hand sb");
					
				} else if (line.startsWith(bbExp, actIndex)) {
					action.amount = ParseUtil.parseMoney(line, actIndex + bbExp.length());
					seat.bigblind = true;
					assert_(action.amount == hand.bb, "action bb = hand bb");
					
				} else {
					fail("unknown post");
				}
				
				seatPip(seat, action.amount);
				break;
			}
			
			case CALL:
			case BET: {
				// Keynell calls 300
				assert_(line.indexOf(" ", actEndIndex + 1) == -1, "end of line");
				action.amount = ParseUtil.parseMoney(line, actEndIndex + 1);
				seatPip(seat, action.amount);
				break;
			}
			
			case RAISE: {
				// x-G-MONEY raises to 2000
				String raiseExp = "raises to ";
				assert_(line.startsWith(raiseExp, actIndex), "raise exp");
				int amountStart = actIndex + raiseExp.length();
				// subtract what seat has already put in this round
				// otherwise would double count
				// have to do inverse when replaying..
				action.amount = ParseUtil.parseMoney(line, amountStart) - seatPip(seat);
				seatPip(seat, action.amount);
				break;
			}
			
			case FOLD: {
				// Keynell folds
				assert_(line.indexOf(" ", actEndIndex) == -1, "line complete");
				// int handStart = line.indexOf("[", actEnd);
				// if (handStart > 0) {
				// String[] cards = ParseUtil.parseCards(line, handStart);
				// seat.finalHoleCards =
				// ParseUtil.checkCards(seat.finalHoleCards,
				// ParseUtil.getHoleCards(hand.game.type, cards));
				// seat.finalUpCards = ParseUtil.checkCards(seat.finalUpCards,
				// ParseUtil.getUpCards(hand.game.type, cards));
				// }
				break;
			}
			
			case SHOW: {
				// bombermango shows [Ah Ad]
				// bombermango shows two pair, Aces and Sevens
				if (line.indexOf("[", actEndIndex + 1) > 0) {
					String[] cards = ParseUtil.parseCards(line, actEndIndex + 1);
					seat.finalHoleCards = ParseUtil.checkCards(seat.finalHoleCards,
							ParseUtil.getHoleCards(hand.game.type, cards));
					seat.finalUpCards = ParseUtil.checkCards(seat.finalUpCards,
							ParseUtil.getUpCards(hand.game.type, cards));
				}
				break;
			}
			
			case COLLECT: {
				// stoliarenko1 wins the pot (2535) with a full house, Twos full
				// of Sevens
				int braIndex = line.indexOf("(", actEndIndex + 1);
				int amount = ParseUtil.parseMoney(line, braIndex + 1);
				seat.won += amount;
				
				// add the collect as a fake action so the action amounts sum to
				// pot size
				action.amount = -amount;
				
				if (line.indexOf("with", braIndex) > 0) {
					// assume this means it was a showdown and not just a flash
					showdown = true;
				}
				
				break;
			}
			
			default:
				fail("action " + action.type);
		}
		
		// any betting action can cause this
		if (line.endsWith("and is all in")) {
			action.allin = true;
		}
		
		println("action " + action.toString());
		currentStreet().add(action);
	}
	
	private void parseSeat () {
		if (!summaryPhase) {
			// Seat 3: Keynell (90000)
			int seatno = ParseUtil.parseInt(line, 5);
			int col = line.indexOf(": ");
			int braStart = line.lastIndexOf("(");
			
			Seat seat = new Seat();
			seat.num = (byte) seatno;
			seat.name = StringCache.get(line.substring(col + 2, braStart - 1));
			seat.chips = ParseUtil.parseMoney(line, braStart + 1);
			seatsMap.put(seat.name, seat);
		}
		assert_(seatsMap.size() <= hand.game.max, "seats < max");
	}
	
	private void parseHand () {
		assert_(hand == null, "finished last hand");
		
		Matcher m = FTHandRe.pattern.matcher(line);
		if (!m.matches()) {
			throw new RuntimeException("does not match: " + line);
		}
		
		println("hid=" + m.group(FTHandRe.hid));
		println("tname=" + m.group(FTHandRe.tname));
		println("tid=" + m.group(FTHandRe.tid));
		println("table=" + m.group(FTHandRe.table));
		println("tabletype=" + m.group(FTHandRe.tabletype));
		println("sb=" + m.group(FTHandRe.sb));
		println("bb=" + m.group(FTHandRe.bb));
		println("ante=" + m.group(FTHandRe.ante));
		println("lim=" + m.group(FTHandRe.lim));
		println("game=" + m.group(FTHandRe.game));
		println("date1=" + m.group(FTHandRe.date1));
		println("date2=" + m.group(FTHandRe.date2));
		
		hand = new Hand();
		hand.id = Long.parseLong(m.group(FTHandRe.hid));
		hand.tablename = m.group(FTHandRe.table);
		hand.sb = ParseUtil.parseMoney(m.group(FTHandRe.sb), 0);
		hand.bb = ParseUtil.parseMoney(m.group(FTHandRe.bb), 0);
		String ante = m.group(FTHandRe.ante);
		if (ante != null) {
			hand.antes = ParseUtil.parseMoney(ante, 0);
		}
		
		Game game = new Game();
		game.currency = ParseUtil.parseCurrency(m.group(FTHandRe.sb), 0);
		game.sb = hand.sb;
		game.bb = hand.bb;
		game.limit = ParseUtil.getLimitType(m.group(FTHandRe.lim));
		game.type = ParseUtil.getGameType(m.group(FTHandRe.game));
		String tabletype = m.group(FTHandRe.tabletype);
		if (tabletype != null && tabletype.contains("heads up")) {
			game.max = 2;
		} else if (tabletype != null && tabletype.matches("\\d max")) {
			game.max = Integer.parseInt(tabletype.substring(0, 1));
		} else {
			// ehhhh
			game.max = 9;
		}
		hand.game = history.getGame(game);
		
		newStreet();
	}
	
}