/*
 * opsu! - an open-source osu! client
 * Copyright (C) 2014, 2015 Jeffrey Han
 *
 * opsu! is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * opsu! is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with opsu!.  If not, see <http://www.gnu.org/licenses/>.
 */

package itdelatrisu.opsu;

import itdelatrisu.opsu.audio.HitSound;
import itdelatrisu.opsu.audio.MusicController;
import itdelatrisu.opsu.audio.SoundController;
import itdelatrisu.opsu.audio.SoundEffect;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import org.newdawn.slick.Animation;
import org.newdawn.slick.Color;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.Image;

/**
 * Holds score data and renders all score-related elements.
 */
public class GameScore {
	/** Delta multiplier for steady HP drain. */
	public static final float HP_DRAIN_MULTIPLIER = 1 / 200f;

	/** Letter grades. */
	private enum Grade {
		NULL (null, null),
		SS  (GameImage.RANKING_SS,  GameImage.RANKING_SS_SMALL),
		SSH (GameImage.RANKING_SSH, GameImage.RANKING_SSH_SMALL), // silver
		S   (GameImage.RANKING_S,   GameImage.RANKING_S_SMALL),
		SH  (GameImage.RANKING_SH,  GameImage.RANKING_SH_SMALL),  // silver
		A   (GameImage.RANKING_A,   GameImage.RANKING_A_SMALL),
		B   (GameImage.RANKING_B,   GameImage.RANKING_B_SMALL),
		C   (GameImage.RANKING_C,   GameImage.RANKING_C_SMALL),
		D   (GameImage.RANKING_D,   GameImage.RANKING_D_SMALL);

		/** GameImages associated with this grade (large and small sizes). */
		private GameImage large, small;

		/**
		 * Constructor.
		 * @param large the large size image
		 * @param small the small size image
		 */
		Grade(GameImage large, GameImage small) {
			this.large = large;
			this.small = small;
		}

		/**
		 * Returns the large size grade image.
		 */
		public Image getLargeImage() { return large.getImage(); }

		/**
		 * Returns the small size grade image.
		 */
		public Image getSmallImage() { return small.getImage(); }
	}

	/** Hit result types. */
	public static final int
		HIT_MISS     = 0,
		HIT_50       = 1,
		HIT_100      = 2,
		HIT_300      = 3,
		HIT_100K     = 4,   // 100-Katu
		HIT_300K     = 5,   // 300-Katu
		HIT_300G     = 6,   // Geki
		HIT_SLIDER10 = 7,
		HIT_SLIDER30 = 8,
		HIT_MAX      = 9;   // not a hit result

	/** Hit result-related images (indexed by HIT_* constants). */
	private Image[] hitResults;

	/** Counts of each hit result so far. */
	private int[] hitResultCount;

	/** Total number of hit objects so far, not including Katu/Geki (for calculating grade). */
	private int objectCount;

	/** Total objects including slider hits/ticks (for determining Full Combo status). */
	private int fullObjectCount;

	/** The current combo streak. */
	private int combo;

	/** The max combo streak obtained. */
	private int comboMax;

	/**
	 * Hit result types accumulated this streak (bitmask), for Katu/Geki status.
	 * <ul>
	 * <li>&1: 100
	 * <li>&2: 50/Miss
	 * </ul>
	 */
	private byte comboEnd;

	/** Combo burst images. */
	private Image[] comboBurstImages;

	/** Index of the current combo burst image. */
	private int comboBurstIndex;

	/** Alpha level of the current combo burst image (for fade out). */
	private float comboBurstAlpha;

	/** Current x coordinate of the combo burst image (for sliding animation). */
	private int comboBurstX;

	/** List of hit result objects associated with hit objects. */
	private LinkedList<OsuHitObjectResult> hitResultList;

	/**
	 * Hit result helper class.
	 */
	private class OsuHitObjectResult {
		/** Object start time. */
		public int time;

		/** Hit result. */
		public int result;

		/** Object coordinates. */
		public float x, y;

		/** Combo color. */
		public Color color;

		/** Alpha level (for fading out). */
		public float alpha = 1f;

		/**
		 * Constructor.
		 * @param time the result's starting track position
		 * @param result the hit result (HIT_* constants)
		 * @param x the center x coordinate
		 * @param y the center y coordinate
		 * @param color the color of the hit object
		 */
		public OsuHitObjectResult(int time, int result, float x, float y, Color color) {
			this.time = time;
			this.result = result;
			this.x = x;
			this.y = y;
			this.color = color;
		}
	}

	/** Current game score. */
	private long score;

	/** Displayed game score (for animation, slightly behind score). */
	private long scoreDisplay;

	/** Current health bar percentage. */
	private float health;

	/** Displayed health (for animation, slightly behind health). */
	private float healthDisplay;

	/** Beatmap HPDrainRate value. (0:easy ~ 10:hard) */
	private float drainRate = 5f;

	/** Beatmap OverallDifficulty value. (0:easy ~ 10:hard) */
	private float difficulty = 5f;

	/** Default text symbol images. */
	private Image[] defaultSymbols;

	/** Score text symbol images. */
	private HashMap<Character, Image> scoreSymbols;

	/** Scorebar animation. */
	private Animation scorebarColour;

	/** Container dimensions. */
	private int width, height;

	/**
	 * Constructor.
	 * @param width container width
	 * @param height container height
	 */
	public GameScore(int width, int height) {
		this.width = width;
		this.height = height;

		clear();
	}

	/**
	 * Clears all data and re-initializes object.
	 */
	public void clear() {
		score = 0;
		scoreDisplay = 0;
		health = 100f;
		healthDisplay = 100f;
		hitResultCount = new int[HIT_MAX];
		hitResultList = new LinkedList<OsuHitObjectResult>();
		objectCount = 0;
		fullObjectCount = 0;
		combo = 0;
		comboMax = 0;
		comboEnd = 0;
		comboBurstIndex = -1;
	}

	/**
	 * Loads all game score images.
	 * @param dir the image directory
	 */
	public void loadImages(File dir) {
		// combo burst images
		if (GameImage.COMBO_BURST.hasSkinImages() ||
		    (!GameImage.COMBO_BURST.hasSkinImage() && GameImage.COMBO_BURST.getImages() != null))
			comboBurstImages = GameImage.COMBO_BURST.getImages();
		else
			comboBurstImages = new Image[]{ GameImage.COMBO_BURST.getImage() };

		// scorebar-colour animation
		Image[] scorebar = GameImage.SCOREBAR_COLOUR.getImages();
		scorebarColour = (scorebar != null) ? new Animation(scorebar, 60) : null;

		// default symbol images
		defaultSymbols = new Image[10];
		defaultSymbols[0] = GameImage.DEFAULT_0.getImage();
		defaultSymbols[1] = GameImage.DEFAULT_1.getImage();
		defaultSymbols[2] = GameImage.DEFAULT_2.getImage();
		defaultSymbols[3] = GameImage.DEFAULT_3.getImage();
		defaultSymbols[4] = GameImage.DEFAULT_4.getImage();
		defaultSymbols[5] = GameImage.DEFAULT_5.getImage();
		defaultSymbols[6] = GameImage.DEFAULT_6.getImage();
		defaultSymbols[7] = GameImage.DEFAULT_7.getImage();
		defaultSymbols[8] = GameImage.DEFAULT_8.getImage();
		defaultSymbols[9] = GameImage.DEFAULT_9.getImage();

		// score symbol images
		scoreSymbols = new HashMap<Character, Image>(14);
		scoreSymbols.put('0', GameImage.SCORE_0.getImage());
		scoreSymbols.put('1', GameImage.SCORE_1.getImage());
		scoreSymbols.put('2', GameImage.SCORE_2.getImage());
		scoreSymbols.put('3', GameImage.SCORE_3.getImage());
		scoreSymbols.put('4', GameImage.SCORE_4.getImage());
		scoreSymbols.put('5', GameImage.SCORE_5.getImage());
		scoreSymbols.put('6', GameImage.SCORE_6.getImage());
		scoreSymbols.put('7', GameImage.SCORE_7.getImage());
		scoreSymbols.put('8', GameImage.SCORE_8.getImage());
		scoreSymbols.put('9', GameImage.SCORE_9.getImage());
		scoreSymbols.put(',', GameImage.SCORE_COMMA.getImage());
		scoreSymbols.put('.', GameImage.SCORE_DOT.getImage());
		scoreSymbols.put('%', GameImage.SCORE_PERCENT.getImage());
		scoreSymbols.put('x', GameImage.SCORE_X.getImage());

		// hit result images
		hitResults = new Image[HIT_MAX];
		hitResults[HIT_MISS]     = GameImage.HIT_MISS.getImage();
		hitResults[HIT_50]       = GameImage.HIT_50.getImage();
		hitResults[HIT_100]      = GameImage.HIT_100.getImage();
		hitResults[HIT_300]      = GameImage.HIT_300.getImage();
		hitResults[HIT_100K]     = GameImage.HIT_100K.getImage();
		hitResults[HIT_300K]     = GameImage.HIT_300K.getImage();
		hitResults[HIT_300G]     = GameImage.HIT_300G.getImage();
		hitResults[HIT_SLIDER10] = GameImage.HIT_SLIDER10.getImage();
		hitResults[HIT_SLIDER30] = GameImage.HIT_SLIDER30.getImage();
	}

	/**
	 * Returns a default/score text symbol image for a character.
	 */
	public Image getDefaultSymbolImage(int i) { return defaultSymbols[i]; }
	public Image getScoreSymbolImage(char c) { return scoreSymbols.get(c); }

	/**
	 * Sets or returns the health drain rate.
	 */
	public void setDrainRate(float drainRate) { this.drainRate = drainRate; }
	public float getDrainRate() { return drainRate; }

	/**
	 * Sets or returns the difficulty.
	 */
	public void setDifficulty(float difficulty) { this.difficulty = difficulty; }
	public float getDifficulty() { return difficulty; }

	/**
	 * Draws a number with defaultSymbols.
	 * @param n the number to draw
	 * @param x the center x coordinate
	 * @param y the center y coordinate
	 * @param scale the scale to apply
	 */
	public void drawSymbolNumber(int n, float x, float y, float scale) {
		int length = (int) (Math.log10(n) + 1);
		float digitWidth = getDefaultSymbolImage(0).getWidth() * scale;
		float cx = x + ((length - 1) * (digitWidth / 2));

		for (int i = 0; i < length; i++) {
			getDefaultSymbolImage(n % 10).getScaledCopy(scale).drawCentered(cx, y);
			cx -= digitWidth;
			n /= 10;
		}
	}

	/**
	 * Draws a string of scoreSymbols.
	 * @param str the string to draw
	 * @param x the starting x coordinate
	 * @param y the y coordinate
	 * @param scale the scale to apply
	 * @param rightAlign align right (true) or left (false)
	 */
	private void drawSymbolString(String str, int x, int y, float scale, boolean rightAlign) {
		char[] c = str.toCharArray();
		int cx = x;
		if (rightAlign) {
			for (int i = c.length - 1; i >= 0; i--) {
				Image digit = getScoreSymbolImage(c[i]);
				if (scale != 1.0f)
					digit = digit.getScaledCopy(scale);
				cx -= digit.getWidth();
				digit.draw(cx, y);
			}
		} else {
			for (int i = 0; i < c.length; i++) {
				Image digit = getScoreSymbolImage(c[i]);
				if (scale != 1.0f)
					digit = digit.getScaledCopy(scale);
				digit.draw(cx, y);
				cx += digit.getWidth();
			}
		}
	}

	/**
	 * Draws game elements:
	 *   scorebar, score, score percentage, map progress circle,
	 *   mod icons, combo count, combo burst, and grade.
	 * @param g the graphics context
	 * @param breakPeriod if true, will not draw scorebar and combo elements, and will draw grade
	 * @param firstObject true if the first hit object's start time has not yet passed
	 */
	public void drawGameElements(Graphics g, boolean breakPeriod, boolean firstObject) {
		int marginX = (int) (width * 0.008f);

		// score
		drawSymbolString((scoreDisplay < 100000000) ? String.format("%08d", scoreDisplay) : Long.toString(scoreDisplay),
				width - marginX, 0, 1.0f, true);

		// score percentage
		int symbolHeight = getScoreSymbolImage('0').getHeight();
		float scorePercent = getScorePercent();
		drawSymbolString(
				String.format((scorePercent < 10f) ? "0%.2f%%" : "%.2f%%", scorePercent),
				width - marginX, symbolHeight, 0.75f, true
		);

		// map progress circle
		g.setAntiAlias(true);
		g.setLineWidth(2f);
		g.setColor(Color.white);
		int circleX = width - marginX - (  // max width: "100.00%"
				getScoreSymbolImage('1').getWidth() +
				getScoreSymbolImage('0').getWidth() * 4 +
				getScoreSymbolImage('.').getWidth() +
				getScoreSymbolImage('%').getWidth()
		);
		float circleDiameter = symbolHeight * 0.75f;
		g.drawOval(circleX, symbolHeight, circleDiameter, circleDiameter);

		OsuFile osu = MusicController.getOsuFile();
		int firstObjectTime = osu.objects[0].getTime();
		int trackPosition = MusicController.getPosition();
		if (trackPosition > firstObjectTime) {
			// map progress (white)
			g.fillArc(circleX, symbolHeight, circleDiameter, circleDiameter,
					-90, -90 + (int) (360f * (trackPosition - firstObjectTime) / (osu.endTime - firstObjectTime))
			);
		} else {
			// lead-in time (yellow)
			g.setColor(Utils.COLOR_YELLOW_ALPHA);
			g.fillArc(circleX, symbolHeight, circleDiameter, circleDiameter,
					-90 + (int) (360f * trackPosition / firstObjectTime), -90
			);
		}

		// mod icons
		if ((firstObject && trackPosition < firstObjectTime) || GameMod.AUTO.isActive()) {
			int modWidth = GameMod.AUTO.getImage().getWidth();
			float modX = (width * 0.98f) - modWidth;
			int modCount = 0;
			for (GameMod mod : GameMod.VALUES_REVERSED) {
				if (mod.isActive()) {
					mod.getImage().draw(
							modX - (modCount * (modWidth / 2f)),
							symbolHeight + circleDiameter + 10
					);
					modCount++;
				}
			}
		}

		if (!breakPeriod) {
			// scorebar
			// TODO: these might need to be scaled by cropping the empty (transparent) space around the images...
			float healthRatio = healthDisplay / 100f;
			if (firstObject) {  // gradually move ki before map begins
				if (firstObjectTime >= 1500 && trackPosition < firstObjectTime - 500)
					healthRatio = (float) trackPosition / (firstObjectTime - 500);
			}
			Image scorebar = GameImage.SCOREBAR_BG.getImage();
			Image colour = (scorebarColour != null) ?
					scorebarColour.getCurrentFrame() :
					GameImage.SCOREBAR_COLOUR.getImage();
			float colourX = scorebar.getWidth() * 0.017f, colourY = scorebar.getHeight() * 0.3f;
			scorebar.draw(0, 0);
			Image colourCropped = colour.getSubImage(0, 0, (int) (colour.getWidth() * healthRatio), colour.getHeight());
			colourCropped.draw(colourX, colourY);

			Image ki = null;
			if (health >= 50f)
				ki = GameImage.SCOREBAR_KI.getImage();
			else if (health >= 25f)
				ki = GameImage.SCOREBAR_KI_DANGER.getImage();
			else
				ki = GameImage.SCOREBAR_KI_DANGER2.getImage();
			ki.drawCentered(colourX + colourCropped.getWidth(), ki.getHeight() / 2f);

			// combo burst
			if (comboBurstIndex != -1 && comboBurstAlpha > 0f) {
				Image comboBurst = comboBurstImages[comboBurstIndex];
				comboBurst.setAlpha(comboBurstAlpha);
				comboBurstImages[comboBurstIndex].draw(comboBurstX, height - comboBurst.getHeight());
			}

			// combo count
			if (combo > 0)  // 0 isn't a combo
				drawSymbolString(String.format("%dx", combo), 10, height - 10 - symbolHeight, 1.0f, false);
		} else {
			// grade
			Grade grade = getGrade();
			if (grade != Grade.NULL) {
				Image gradeImage = grade.getSmallImage();
				float gradeScale = symbolHeight * 0.75f / gradeImage.getHeight();
				gradeImage.getScaledCopy(gradeScale).draw(
						circleX - gradeImage.getWidth(), symbolHeight
				);
			}
		}
	}

	/**
	 * Draws ranking elements: score, results, ranking.
	 * @param g the graphics context
	 * @param width the width of the container
	 * @param height the height of the container
	 */
	public void drawRankingElements(Graphics g, int width, int height) {
		// grade
		Grade grade = getGrade();
		if (grade != Grade.NULL) {
			Image gradeImage = grade.getLargeImage();
			float gradeScale = (height * 0.5f) / gradeImage.getHeight();
			gradeImage = gradeImage.getScaledCopy(gradeScale);
			gradeImage.draw(width - gradeImage.getWidth(), height * 0.09f);
		}

		// header & "Ranking" text
		Image rankingTitle = GameImage.RANKING_TITLE.getImage();
		float rankingHeight = (rankingTitle.getHeight() * 0.75f) + 3;
		g.setColor(Utils.COLOR_BLACK_ALPHA);
		g.fillRect(0, 0, width, rankingHeight);
		rankingTitle.draw((width * 0.97f) - rankingTitle.getWidth(), 0);

		// ranking panel
		Image rankingPanel = GameImage.RANKING_PANEL.getImage();
		int rankingPanelWidth  = rankingPanel.getWidth();
		int rankingPanelHeight = rankingPanel.getHeight();
		rankingPanel.draw(0, rankingHeight - (rankingHeight / 10f));

		float symbolTextScale = (height / 15f) / getScoreSymbolImage('0').getHeight();
		float rankResultScale = (height * 0.03f) / hitResults[HIT_300].getHeight();

		// score
		drawSymbolString((score < 100000000) ? String.format("%08d", score) : Long.toString(score),
				(int) (width * 0.18f), height / 6, symbolTextScale, false);

		// result counts
		float resultInitialX = rankingPanelWidth * 0.20f;
		float resultInitialY = rankingHeight + (rankingPanelHeight * 0.27f) + (rankingHeight / 10f);
		float resultHitInitialX = rankingPanelWidth * 0.05f;
		float resultHitInitialY = resultInitialY + (getScoreSymbolImage('0').getHeight() * symbolTextScale);
		float resultOffsetX = rankingPanelWidth / 2f;
		float resultOffsetY = rankingPanelHeight * 0.2f;

		int[] rankDrawOrder = { HIT_300, HIT_300G, HIT_100, HIT_100K, HIT_50, HIT_MISS };
		int[] rankResultOrder = {
				hitResultCount[HIT_300], hitResultCount[HIT_300G],
				hitResultCount[HIT_100], hitResultCount[HIT_100K] + hitResultCount[HIT_300K],
				hitResultCount[HIT_50], hitResultCount[HIT_MISS]
		};

		for (int i = 0; i < rankDrawOrder.length; i += 2) {
			hitResults[rankDrawOrder[i]].getScaledCopy(rankResultScale).draw(
					resultHitInitialX, resultHitInitialY - (hitResults[rankDrawOrder[i]].getHeight() * rankResultScale) + (resultOffsetY * (i / 2)));
			hitResults[rankDrawOrder[i+1]].getScaledCopy(rankResultScale).draw(
					resultHitInitialX + resultOffsetX, resultHitInitialY - (hitResults[rankDrawOrder[i]].getHeight() * rankResultScale) + (resultOffsetY * (i / 2)));
			drawSymbolString(String.format("%dx", rankResultOrder[i]),
					(int) resultInitialX, (int) (resultInitialY + (resultOffsetY * (i / 2))), symbolTextScale, false);
			drawSymbolString(String.format("%dx", rankResultOrder[i+1]),
					(int) (resultInitialX + resultOffsetX), (int) (resultInitialY + (resultOffsetY * (i / 2))), symbolTextScale, false);
		}

		// combo and accuracy
		Image rankingMaxCombo = GameImage.RANKING_MAXCOMBO.getImage();
		Image rankingAccuracy = GameImage.RANKING_ACCURACY.getImage();
		float textY = rankingHeight + (rankingPanelHeight * 0.87f) - (rankingHeight / 10f);
		float numbersX = rankingMaxCombo.getWidth() * .07f;
		float numbersY = textY + rankingMaxCombo.getHeight() * 0.7f;
		rankingMaxCombo.draw(width * 0.01f, textY);
		rankingAccuracy.draw(rankingPanelWidth / 2f, textY);
		drawSymbolString(String.format("%dx", comboMax),
				(int) (width * 0.01f + numbersX), (int) numbersY, symbolTextScale, false);
		drawSymbolString(String.format("%02.2f%%", getScorePercent()),
				(int) (rankingPanelWidth / 2f + numbersX), (int) numbersY, symbolTextScale, false);

		// full combo
		if (combo == fullObjectCount) {
			GameImage.RANKING_PERFECT.getImage().draw(
					width * 0.08f,
					(height * 0.99f) - GameImage.RANKING_PERFECT.getImage().getHeight()
			);
		}
	}

	/**
	 * Draws stored hit results and removes them from the list as necessary.
	 * @param trackPosition the current track position
	 */
	public void drawHitResults(int trackPosition) {
		final int fadeDelay = 500;

		Iterator<OsuHitObjectResult> iter = hitResultList.iterator();
		while (iter.hasNext()) {
			OsuHitObjectResult hitResult = iter.next();
			if (hitResult.time + fadeDelay > trackPosition) {
				hitResults[hitResult.result].setAlpha(hitResult.alpha);
				hitResult.alpha = 1 - ((float) (trackPosition - hitResult.time) / fadeDelay);
				hitResults[hitResult.result].drawCentered(hitResult.x, hitResult.y);

				// hit lighting
				if (Options.isHitLightingEnabled() && hitResult.result != HIT_MISS &&
					hitResult.result != HIT_SLIDER30 && hitResult.result != HIT_SLIDER10) {
					float scale = 1f + ((trackPosition - hitResult.time) / (float) fadeDelay);
					Image scaledLighting  = GameImage.LIGHTING.getImage().getScaledCopy(scale);
					Image scaledLighting1 = GameImage.LIGHTING1.getImage().getScaledCopy(scale);
					scaledLighting.draw(hitResult.x - (scaledLighting.getWidth() / 2f),
							hitResult.y - (scaledLighting.getHeight() / 2f), hitResult.color);
					scaledLighting1.draw(hitResult.x - (scaledLighting1.getWidth() / 2f),
							hitResult.y - (scaledLighting1.getHeight() / 2f), hitResult.color);
				}
			} else
				iter.remove();
		}
	}

	/**
	 * Changes health by a given percentage, modified by drainRate.
	 */
	public void changeHealth(float percent) {
		// TODO: drainRate formula
		health += percent;
		if (health > 100f)
			health = 100f;
		else if (health < 0f)
			health = 0f;
	}

	/**
	 * Returns health percentage.
	 */
	public float getHealth() { return health; }

	/**
	 * Returns false if health is zero.
	 * If "No Fail" or "Auto" mods are active, this will always return true.
	 */
	public boolean isAlive() {
		return (health > 0f || GameMod.NO_FAIL.isActive() || GameMod.AUTO.isActive());
	}

	/**
	 * Changes score by a raw value (not affected by other modifiers).
	 */
	public void changeScore(int value) { score += value; }

	/**
	 * Returns score percentage (raw score only).
	 */
	private float getScorePercent() {
		float percent = 0;
		if (objectCount > 0)
			percent = ((hitResultCount[HIT_50] * 50) + (hitResultCount[HIT_100] * 100)
					+ (hitResultCount[HIT_300] * 300)) / (objectCount * 300f) * 100f;
		return percent;
	}

	/**
	 * Returns letter grade based on score data,
	 * or Grade.NULL if no objects have been processed.
	 * @return the current Grade
	 */
	private Grade getGrade() {
		if (objectCount < 1)  // avoid division by zero
			return Grade.NULL;

		// TODO: silvers
		float percent = getScorePercent();
		float hit300ratio = hitResultCount[HIT_300] * 100f / objectCount;
		float hit50ratio  = hitResultCount[HIT_50] * 100f / objectCount;
		boolean noMiss    = (hitResultCount[HIT_MISS] == 0);
		if (percent >= 100f)
			return Grade.SS;
		else if (hit300ratio >= 90f && hit50ratio < 1.0f && noMiss)
			return Grade.S;
		else if ((hit300ratio >= 80f && noMiss) || hit300ratio >= 90f)
			return Grade.A;
		else if ((hit300ratio >= 70f && noMiss) || hit300ratio >= 80f)
			return Grade.B;
		else if (hit300ratio >= 60f)
			return Grade.C;
		else
			return Grade.D;
	}

	/**
	 * Updates the score, health, and combo burst displays based on a delta value.
	 * @param delta the delta interval since the last call
	 */
	public void updateDisplays(int delta) {
		// score display
		if (scoreDisplay < score) {
			scoreDisplay += (score - scoreDisplay) * delta / 50 + 1;
			if (scoreDisplay > score)
				scoreDisplay = score;
		}

		// health display
		if (healthDisplay != health) {
			float shift = delta / 15f;
			if (healthDisplay < health) {
				healthDisplay += shift;
				if (healthDisplay > health)
					healthDisplay = health;
			} else {
				healthDisplay -= shift;
				if (healthDisplay < health)
					healthDisplay = health;
			}
		}

		// combo burst
		if (comboBurstIndex > -1 && Options.isComboBurstEnabled()) {
			int leftX  = 0;
			int rightX = width - comboBurstImages[comboBurstIndex].getWidth();
			if (comboBurstX < leftX) {
				comboBurstX += (delta / 2f);
				if (comboBurstX > leftX)
					comboBurstX = leftX;
			} else if (comboBurstX > rightX) {
				comboBurstX -= (delta / 2f);
				if (comboBurstX < rightX)
					comboBurstX = rightX;
			} else if (comboBurstAlpha > 0f) {
				comboBurstAlpha -= (delta / 1200f);
				if (comboBurstAlpha < 0f)
					comboBurstAlpha = 0f;
			}
		}
	}

	/**
	 * Increases the combo streak by one.
	 */
	private void incrementComboStreak() {
		combo++;
		if (combo > comboMax)
			comboMax = combo;

		// combo bursts (at 30, 60, 100+50x)
		if (Options.isComboBurstEnabled() &&
			(combo == 30 || combo == 60 || (combo >= 100 && combo % 50 == 0))) {
			if (combo == 30)
				comboBurstIndex = 0;
			else
				comboBurstIndex = (comboBurstIndex + 1) % comboBurstImages.length;
			comboBurstAlpha = 0.8f;
			if ((comboBurstIndex % 2) == 0)
				comboBurstX = width;
			else
				comboBurstX = comboBurstImages[0].getWidth() * -1;
		}
	}

	/**
	 * Resets the combo streak to zero.
	 */
	private void resetComboStreak() {
		if (combo >= 20)
			SoundController.playSound(SoundEffect.COMBOBREAK);
		combo = 0;
		if (GameMod.SUDDEN_DEATH.isActive())
			health = 0f;
	}

	/**
	 * Handles a slider tick result.
	 * @param time the tick start time
	 * @param result the hit result (HIT_* constants)
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param hitSound the object's hit sound
	 */
	public void sliderTickResult(int time, int result, float x, float y, byte hitSound) {
		int hitValue = 0;
		switch (result) {
		case HIT_SLIDER30:
			hitValue = 30;
			incrementComboStreak();
			changeHealth(1f);
			SoundController.playHitSound(hitSound);
			break;
		case HIT_SLIDER10:
			hitValue = 10;
			incrementComboStreak();
			SoundController.playHitSound(HitSound.SLIDERTICK);
			break;
		case HIT_MISS:
			resetComboStreak();
			break;
		default:
			return;
		}
		fullObjectCount++;

		if (hitValue > 0) {
			score += hitValue;
			if (!Options.isPerfectHitBurstEnabled())
				;  // hide perfect hit results
			else
				hitResultList.add(new OsuHitObjectResult(time, result, x, y, null));
		}
	}

	/**
	 * Handles a hit result.
	 * @param time the object start time
	 * @param result the hit result (HIT_* constants)
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param color the combo color
	 * @param end true if this is the last hit object in the combo
	 * @param hitSound the object's hit sound
	 */
	public void hitResult(int time, int result, float x, float y, Color color,
			boolean end, byte hitSound) {
		int hitValue = 0;
		boolean perfectHit = false;
		switch (result) {
		case HIT_300:
			perfectHit = true;
			hitValue = 300;
			changeHealth(5f);
			objectCount++;
			break;
		case HIT_100:
			hitValue = 100;
			changeHealth(2f);
			comboEnd |= 1;
			objectCount++;
			break;
		case HIT_50:
			hitValue = 50;
			comboEnd |= 2;
			objectCount++;
			break;
		case HIT_MISS:
			hitValue = 0;
			changeHealth(-10f);
			comboEnd |= 2;
			resetComboStreak();
			objectCount++;
			break;
		default:
			return;
		}
		if (hitValue > 0) {
			SoundController.playHitSound(hitSound);

			// game mod score multipliers
			float modMultiplier = 1f;
			for (GameMod mod : GameMod.values()) {
				if (mod.isActive())
					modMultiplier *= mod.getMultiplier();
			}

			/**
			 * [SCORE FORMULA]
			 * Score = Hit Value + Hit Value * (Combo * Difficulty * Mod) / 25
			 * - Hit Value: hit result (50, 100, 300), slider ticks, spinner bonus
			 * - Combo: combo before this hit - 1 (minimum 0)
			 * - Difficulty: the beatmap difficulty
			 * - Mod: mod multipliers
			 */
			score += (hitValue + (hitValue * (Math.max(combo - 1, 0) * difficulty * modMultiplier) / 25));
			incrementComboStreak();
		}
		hitResultCount[result]++;
		fullObjectCount++;

		// last element in combo: check for Geki/Katu
		if (end) {
			if (comboEnd == 0) {
				result = HIT_300G;
				changeHealth(15f);
				hitResultCount[result]++;
			} else if ((comboEnd & 2) == 0) {
				if (result == HIT_100) {
					result = HIT_100K;
					changeHealth(10f);
					hitResultCount[result]++;
				} else if (result == HIT_300) {
					result = HIT_300K;
					changeHealth(10f);
					hitResultCount[result]++;
				}
			}
			comboEnd = 0;
		}

		if (perfectHit && !Options.isPerfectHitBurstEnabled())
			;  // hide perfect hit results
		else
			hitResultList.add(new OsuHitObjectResult(time, result, x, y, color));
	}
}