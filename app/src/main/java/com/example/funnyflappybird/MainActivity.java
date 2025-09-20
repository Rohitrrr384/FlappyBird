package com.example.funnyflappybird;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends Activity {

    GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gameView = new GameView(this);
        setContentView(gameView);
    }

    class GameView extends SurfaceView implements Runnable {
        Thread gameThread = null;
        SurfaceHolder ourHolder;
        volatile boolean playing;
        Canvas canvas;
        Paint paint;

        // Screen dimensions
        int screenX, screenY;

        // Bird properties
        Bird bird;

        // Pipes and obstacles
        ArrayList<Pipe> pipes;
        ArrayList<Obstacle> obstacles;
        ArrayList<Coin> coins;

        // Game state
        boolean gameOver = false;
        int score = 0;
        int highScore = 0;
        int coins_collected = 0;
        int level = 1;

        // Progressive difficulty
        float baseSpeed = 6f;
        float currentSpeed = 6f;
        float speedIncrement = 0.5f;
        long levelUpTime = 0;

        // Funny features
        ArrayList<FunnyEffect> funnyEffects;
        ArrayList<ChatBubble> chatBubbles;
        String[] funnyMessages = {
                "OOPS! 😅", "Nice try! 😂", "Almost there! 🤣",
                "Keep going! 💪", "You're flying! 🚀", "Super bird! 🦸",
                "Fantastic! ✨", "Amazing! 🌟", "Epic fail! 😜",
                "Don't give up! 💖", "You got this! 🔥", "Flying high! 🎈"
        };
        String[] levelUpMessages = {
                "LEVEL UP! 🎉", "Getting faster! 🏃", "Speed boost! ⚡",
                "Challenge accepted! 🎯", "Next level! 🆙", "Difficulty rising! 📈"
        };

        // Particles and effects
        ArrayList<Particle> particles;
        ArrayList<PowerUp> powerUps;
        ArrayList<Emoji> flyingEmojis;

        // Timing
        long frameTime;
        int fps = 60;
        long lastFunnyEffectTime = 0;
        long lastCoinTime = 0;

        // Enhanced color system
        int currentTheme = 0;
        boolean rainbowMode = false;
        long rainbowTime = 0;
        long themeChangeTime = 0;
        boolean discoMode = false;
        long discoTime = 0;

        // Sound effects
        SoundPool soundPool;
        int flapSound, scoreSound, gameOverSound, powerUpSound;
        int coinSound, levelUpSound, funnySound;
        boolean soundEnabled = true;

        // Screen shake effect
        float shakeOffset = 0;
        long shakeTime = 0;

        Random random = new Random();

        public GameView(Context context) {
            super(context);
            ourHolder = getHolder();
            paint = new Paint();
            paint.setAntiAlias(true);

            // Initialize sound
            soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);

            // Get screen dimensions
            screenX = getResources().getDisplayMetrics().widthPixels;
            screenY = getResources().getDisplayMetrics().heightPixels;

            // Initialize game objects
            bird = new Bird(screenX, screenY);
            pipes = new ArrayList<>();
            obstacles = new ArrayList<>();
            coins = new ArrayList<>();
            particles = new ArrayList<>();
            powerUps = new ArrayList<>();
            funnyEffects = new ArrayList<>();
            chatBubbles = new ArrayList<>();
            flyingEmojis = new ArrayList<>();

            // Start the first pipe
            pipes.add(new Pipe(screenX, screenY, currentSpeed));
        }

        @Override
        public void run() {
            while (playing) {
                long startFrameTime = System.currentTimeMillis();

                if (!gameOver) {
                    update();
                }
                draw();

                frameTime = System.currentTimeMillis() - startFrameTime;
                if (frameTime >= 1) {
                    fps = (int) (1000 / frameTime);
                }
            }
        }

        public void update() {
            updateDifficulty();
            bird.update();

            // Update pipes
            for (int i = pipes.size() - 1; i >= 0; i--) {
                Pipe pipe = pipes.get(i);
                pipe.update(currentSpeed);

                // Check collision
                if (pipe.collidesWith(bird)) {
                    if (!bird.hasShield) {
                        addFunnyEffect("BONK! 💥", pipe.x, pipe.topHeight + pipe.gap/2);
                        shakeScreen();
                        gameOver();
                        return;
                    } else {
                        addFunnyEffect("SHIELD! 🛡️", pipe.x, pipe.topHeight + pipe.gap/2);
                    }
                }

                // Check if bird passed pipe
                if (!pipe.scored && pipe.x + pipe.width < bird.x) {
                    pipe.scored = true;
                    score++;
                    addScoreParticles();
                    addChatBubble(funnyMessages[random.nextInt(funnyMessages.length)]);

                    // Special effects for milestones
                    if (score % 10 == 0) {
                        discoMode = true;
                        discoTime = System.currentTimeMillis();
                        addFireworks();
                        addFunnyEffect("DISCO TIME! 🕺", screenX/2, screenY/3);
                    } else if (score % 5 == 0) {
                        rainbowMode = true;
                        rainbowTime = System.currentTimeMillis();
                        addFireworks();
                    }

                    // Add power-up occasionally
                    if (random.nextInt(8) == 0) {
                        powerUps.add(new PowerUp(screenX, screenY));
                    }
                }

                // Remove off-screen pipes
                if (pipe.x + pipe.width < 0) {
                    pipes.remove(i);
                }
            }

            // Add new pipes
            if (pipes.size() == 0 || pipes.get(pipes.size() - 1).x < screenX - 400) {
                pipes.add(new Pipe(screenX, screenY, currentSpeed));
            }

            // Update and add coins
            updateCoins();

            // Update obstacles
            updateObstacles();

            // Update particles
            for (int i = particles.size() - 1; i >= 0; i--) {
                Particle particle = particles.get(i);
                particle.update();
                if (particle.life <= 0) {
                    particles.remove(i);
                }
            }

            // Update power-ups
            updatePowerUps();

            // Update funny effects
            for (int i = funnyEffects.size() - 1; i >= 0; i--) {
                FunnyEffect effect = funnyEffects.get(i);
                effect.update();
                if (effect.life <= 0) {
                    funnyEffects.remove(i);
                }
            }

            // Update chat bubbles
            for (int i = chatBubbles.size() - 1; i >= 0; i--) {
                ChatBubble bubble = chatBubbles.get(i);
                bubble.update();
                if (bubble.life <= 0) {
                    chatBubbles.remove(i);
                }
            }

            // Update flying emojis
            for (int i = flyingEmojis.size() - 1; i >= 0; i--) {
                Emoji emoji = flyingEmojis.get(i);
                emoji.update();
                if (emoji.x < -50) {
                    flyingEmojis.remove(i);
                }
            }

            // Add random flying emojis
            if (random.nextInt(300) == 0) {
                flyingEmojis.add(new Emoji(screenX, screenY));
            }

            // Check ground collision
            if (bird.y > screenY - 100 || bird.y < 0) {
                addFunnyEffect("CRASH! 💥", bird.x, bird.y);
                shakeScreen();
                gameOver();
            }

            // Update rainbow mode
            if (rainbowMode && System.currentTimeMillis() - rainbowTime > 5000) {
                rainbowMode = false;
            }

            // Update disco mode
            if (discoMode && System.currentTimeMillis() - discoTime > 8000) {
                discoMode = false;
            }
        }

        private void updateDifficulty() {
            // Increase speed every 10 points
            int newLevel = (score / 10) + 1;
            if (newLevel > level) {
                level = newLevel;
                currentSpeed = baseSpeed + (level - 1) * speedIncrement;
                addChatBubble(levelUpMessages[random.nextInt(levelUpMessages.length)]);
                addFunnyEffect("SPEED UP! ⚡", screenX/2, screenY/2);
                levelUpTime = System.currentTimeMillis();

                // Cap the speed to keep it playable
                if (currentSpeed > 15f) {
                    currentSpeed = 15f;
                }
            }
        }

        private void updateCoins() {
            // Add coins occasionally
            if (System.currentTimeMillis() - lastCoinTime > 3000 + random.nextInt(5000)) {
                lastCoinTime = System.currentTimeMillis();
                coins.add(new Coin(screenX, screenY));
            }

            // Update existing coins
            for (int i = coins.size() - 1; i >= 0; i--) {
                Coin coin = coins.get(i);
                coin.update(currentSpeed);

                if (coin.collidesWith(bird)) {
                    coins_collected++;
                    addCoinParticles(coin.x, coin.y);
                    addFunnyEffect("BLING! 💰", coin.x, coin.y);
                    coins.remove(i);
                } else if (coin.x < -50) {
                    coins.remove(i);
                }
            }
        }

        private void updateObstacles() {
            // Add obstacles occasionally at higher levels
            if (level >= 3 && random.nextInt(500) == 0) {
                obstacles.add(new Obstacle(screenX, screenY));
            }

            for (int i = obstacles.size() - 1; i >= 0; i--) {
                Obstacle obstacle = obstacles.get(i);
                obstacle.update(currentSpeed);

                if (obstacle.collidesWith(bird) && !bird.hasShield) {
                    addFunnyEffect("BONK! 🤕", obstacle.x, obstacle.y);
                    shakeScreen();
                    gameOver();
                    return;
                } else if (obstacle.x < -100) {
                    obstacles.remove(i);
                }
            }
        }

        private void updatePowerUps() {
            for (int i = powerUps.size() - 1; i >= 0; i--) {
                PowerUp powerUp = powerUps.get(i);
                powerUp.update(currentSpeed);

                if (powerUp.collidesWith(bird)) {
                    bird.applyPowerUp(powerUp.type);
                    addPowerUpParticles(powerUp.x, powerUp.y);
                    addFunnyEffect("POWER! ⚡", powerUp.x, powerUp.y);
                    powerUps.remove(i);
                } else if (powerUp.x < -50) {
                    powerUps.remove(i);
                }
            }
        }

        private void shakeScreen() {
            shakeTime = System.currentTimeMillis();
            shakeOffset = 10f;
        }

        public void draw() {
            if (ourHolder.getSurface().isValid()) {
                canvas = ourHolder.lockCanvas();

                // Apply screen shake
                if (System.currentTimeMillis() - shakeTime < 500) {
                    float shake = shakeOffset * (float) Math.sin(System.currentTimeMillis() * 0.1);
                    canvas.translate(shake, shake * 0.5f);
                    shakeOffset *= 0.9f;
                }

                // Clear screen with gradient background
                drawGradientBackground();

                // Draw clouds
                drawClouds();

                // Draw flying emojis
                for (Emoji emoji : flyingEmojis) {
                    emoji.draw(canvas, paint);
                }

                // Draw coins
                for (Coin coin : coins) {
                    coin.draw(canvas, paint);
                }

                // Draw obstacles
                for (Obstacle obstacle : obstacles) {
                    obstacle.draw(canvas, paint);
                }

                // Draw pipes with decorations
                for (Pipe pipe : pipes) {
                    pipe.draw(canvas, paint);
                }

                // Draw power-ups
                for (PowerUp powerUp : powerUps) {
                    powerUp.draw(canvas, paint);
                }

                // Draw bird
                bird.draw(canvas, paint);

                // Draw particles
                for (Particle particle : particles) {
                    particle.draw(canvas, paint);
                }

                // Draw funny effects
                for (FunnyEffect effect : funnyEffects) {
                    effect.draw(canvas, paint);
                }

                // Draw chat bubbles
                for (ChatBubble bubble : chatBubbles) {
                    bubble.draw(canvas, paint);
                }

                // Draw ground
                drawGround();

                // Draw UI
                drawUI();

                if (gameOver) {
                    drawGameOverScreen();
                }

                ourHolder.unlockCanvasAndPost(canvas);
            }
        }

        private void drawGradientBackground() {
            updateColorTheme();

            paint.setStyle(Paint.Style.FILL);

            // Create beautiful gradient backgrounds based on theme
            int[] colors = getThemeColors();

            for (int y = 0; y < screenY; y++) {
                float ratio = (float) y / screenY;

                if (discoMode) {
                    // Disco effect - rapid color changes
                    long time = System.currentTimeMillis() / 20;
                    float hue = (time * 10f + y * 2f) % 360;
                    int color = Color.HSVToColor(new float[]{hue, 1.0f, 1.0f});
                    paint.setColor(color);
                } else if (rainbowMode) {
                    // Rainbow gradient effect
                    long time = System.currentTimeMillis() / 50;
                    float hue = (time * 2f + y * 0.5f) % 360;
                    int color = Color.HSVToColor(new float[]{hue, 0.8f, 1.0f});
                    paint.setColor(color);
                } else {
                    // Smooth multi-color gradient
                    int topColor = colors[0];
                    int midColor = colors[1];
                    int bottomColor = colors[2];

                    int r, g, b;
                    if (ratio < 0.5f) {
                        // Top to middle
                        float localRatio = ratio * 2;
                        r = (int) (Color.red(topColor) * (1 - localRatio) + Color.red(midColor) * localRatio);
                        g = (int) (Color.green(topColor) * (1 - localRatio) + Color.green(midColor) * localRatio);
                        b = (int) (Color.blue(topColor) * (1 - localRatio) + Color.blue(midColor) * localRatio);
                    } else {
                        // Middle to bottom
                        float localRatio = (ratio - 0.5f) * 2;
                        r = (int) (Color.red(midColor) * (1 - localRatio) + Color.red(bottomColor) * localRatio);
                        g = (int) (Color.green(midColor) * (1 - localRatio) + Color.green(bottomColor) * localRatio);
                        b = (int) (Color.blue(midColor) * (1 - localRatio) + Color.blue(bottomColor) * localRatio);
                    }
                    paint.setColor(Color.rgb(r, g, b));
                }
                canvas.drawRect(0, y, screenX, y + 3, paint);
            }

            // Add atmospheric effects
            drawAtmosphericEffects();
        }

        private void updateColorTheme() {
            // Change theme every 45 seconds
            long currentTime = System.currentTimeMillis();
            if (currentTime - themeChangeTime > 45000) {
                themeChangeTime = currentTime;
                currentTheme = random.nextInt(6);
            }
        }

        private int[] getThemeColors() {
            switch (currentTheme) {
                case 0: // DAYLIGHT
                    return new int[]{
                            Color.rgb(135, 206, 250), // Light sky blue
                            Color.rgb(173, 216, 230), // Light blue
                            Color.rgb(255, 218, 185)  // Peach
                    };
                case 1: // SUNSET
                    return new int[]{
                            Color.rgb(255, 94, 77),   // Coral
                            Color.rgb(255, 154, 0),   // Orange
                            Color.rgb(255, 206, 84)   // Yellow
                    };
                case 2: // NIGHT
                    return new int[]{
                            Color.rgb(25, 25, 112),   // Midnight blue
                            Color.rgb(72, 61, 139),   // Dark slate blue
                            Color.rgb(123, 104, 238)  // Medium slate blue
                    };
                case 3: // OCEAN
                    return new int[]{
                            Color.rgb(0, 119, 190),   // Ocean blue
                            Color.rgb(0, 180, 216),   // Sky blue
                            Color.rgb(144, 224, 239)  // Light blue
                    };
                case 4: // FOREST
                    return new int[]{
                            Color.rgb(34, 139, 34),   // Forest green
                            Color.rgb(107, 142, 35),  // Olive drab
                            Color.rgb(173, 255, 47)   // Green yellow
                    };
                case 5: // RAINBOW
                default:
                    long time = System.currentTimeMillis() / 1000;
                    return new int[]{
                            Color.HSVToColor(new float[]{(time * 50) % 360, 0.8f, 1.0f}),
                            Color.HSVToColor(new float[]{(time * 50 + 120) % 360, 0.8f, 1.0f}),
                            Color.HSVToColor(new float[]{(time * 50 + 240) % 360, 0.8f, 1.0f})
                    };
            }
        }

        private void drawAtmosphericEffects() {
            switch (currentTheme) {
                case 2: // NIGHT
                    drawStars();
                    break;
                case 1: // SUNSET
                    drawSunGlow();
                    break;
                case 3: // OCEAN
                    drawWaveReflections();
                    break;
            }
        }

        private void drawStars() {
            paint.setColor(Color.WHITE);
            Random starRandom = new Random(12345);
            for (int i = 0; i < 50; i++) {
                float x = starRandom.nextFloat() * screenX;
                float y = starRandom.nextFloat() * (screenY * 0.6f);
                float twinkle = (float) Math.sin(System.currentTimeMillis() * 0.01 + i) * 0.5f + 0.5f;
                paint.setAlpha((int) (255 * twinkle));
                canvas.drawCircle(x, y, 2 + twinkle * 2, paint);
            }
            paint.setAlpha(255);
        }

        private void drawSunGlow() {
            paint.setColor(Color.YELLOW);
            paint.setAlpha(100);
            float sunX = screenX * 0.8f;
            float sunY = screenY * 0.2f;
            canvas.drawCircle(sunX, sunY, 80, paint);
            paint.setAlpha(50);
            canvas.drawCircle(sunX, sunY, 120, paint);
            paint.setAlpha(255);
        }

        private void drawWaveReflections() {
            paint.setColor(Color.WHITE);
            paint.setAlpha(80);
            long time = System.currentTimeMillis() / 100;
            for (int i = 0; i < 5; i++) {
                float waveY = screenY * 0.7f + i * 30;
                float amplitude = 10 + i * 5;
                for (int x = 0; x < screenX; x += 20) {
                    float y = waveY + (float) Math.sin((x + time * 2) * 0.02) * amplitude;
                    canvas.drawCircle(x, y, 3, paint);
                }
            }
            paint.setAlpha(255);
        }

        private void drawClouds() {
            paint.setColor(Color.WHITE);
            paint.setAlpha(150);

            long time = System.currentTimeMillis() / 50;
            for (int i = 0; i < 4; i++) {
                float x = (time * (0.5f + i * 0.1f) + i * 150) % (screenX + 100) - 50;
                float y = 50 + i * 60 + 20 * (float) Math.sin(time * 0.01 + i);
                drawCloud(x, y, 50 + i * 8);
            }
            paint.setAlpha(255);
        }

        private void drawCloud(float x, float y, float size) {
            canvas.drawCircle(x, y, size * 0.6f, paint);
            canvas.drawCircle(x - size * 0.4f, y, size * 0.5f, paint);
            canvas.drawCircle(x + size * 0.4f, y, size * 0.5f, paint);
            canvas.drawCircle(x - size * 0.2f, y - size * 0.3f, size * 0.4f, paint);
            canvas.drawCircle(x + size * 0.2f, y - size * 0.3f, size * 0.4f, paint);
        }

        private void drawGround() {
            // Colorful ground based on theme
            int[] themeColors = getThemeColors();
            paint.setColor(darkenColor(themeColors[2], 0.3f));
            canvas.drawRect(0, screenY - 100, screenX, screenY, paint);

            // Grass details with theme colors
            paint.setColor(darkenColor(themeColors[1], 0.2f));
            Random grassRandom = new Random(54321);
            for (int x = 0; x < screenX; x += 20) {
                for (int i = 0; i < 5; i++) {
                    float grassX = x + grassRandom.nextInt(15);
                    float grassY = screenY - 100 + grassRandom.nextInt(10);
                    canvas.drawLine(grassX, grassY, grassX, grassY - 15, paint);
                }
            }
        }

        private int darkenColor(int color, float factor) {
            int r = (int) (Color.red(color) * (1 - factor));
            int g = (int) (Color.green(color) * (1 - factor));
            int b = (int) (Color.blue(color) * (1 - factor));
            return Color.rgb(r, g, b);
        }

        private void drawUI() {
            // Score with colorful outline
            paint.setColor(Color.BLACK);
            paint.setTextSize(52);
            paint.setTextAlign(Paint.Align.LEFT);
            String scoreText = "Score: " + score;
            canvas.drawText(scoreText, 22, 52, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(50);
            canvas.drawText(scoreText, 20, 50, paint);

            // Coins
            paint.setColor(Color.YELLOW);
            paint.setTextSize(40);
            String coinText = "💰 " + coins_collected;
            canvas.drawText(coinText, 20, 100, paint);

            // Level and speed
            paint.setColor(Color.CYAN);
            paint.setTextSize(35);
            String levelText = "Level: " + level + " Speed: " + String.format("%.1f", currentSpeed);
            canvas.drawText(levelText, 20, 140, paint);

            // FPS counter
            paint.setColor(Color.GREEN);
            paint.setTextSize(25);
            canvas.drawText("FPS: " + fps, screenX - 100, 30, paint);

            // Power-up indicator
            if (bird.powerUpTimer > 0) {
                paint.setColor(Color.MAGENTA);
                paint.setTextSize(40);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("POWER UP! ⚡", screenX / 2f, 200, paint);

                // Rainbow progress bar
                float barWidth = 200;
                float barHeight = 15;
                float barX = (screenX - barWidth) / 2;
                float barY = 210;

                paint.setColor(Color.GRAY);
                canvas.drawRect(barX, barY, barX + barWidth, barY + barHeight, paint);

                float fillWidth = barWidth * (bird.powerUpTimer / 3000f);
                long time = System.currentTimeMillis() / 100;
                for (int i = 0; i < fillWidth; i += 5) {
                    float hue = (time + i * 10) % 360;
                    paint.setColor(Color.HSVToColor(new float[]{hue, 1.0f, 1.0f}));
                    canvas.drawRect(barX + i, barY, barX + i + 5, barY + barHeight, paint);
                }
            }

            // Level up notification
            if (System.currentTimeMillis() - levelUpTime < 3000) {
                paint.setColor(Color.RED);
                paint.setTextSize(60);
                paint.setTextAlign(Paint.Align.CENTER);
                float alpha = 1.0f - (System.currentTimeMillis() - levelUpTime) / 3000f;
                paint.setAlpha((int) (255 * alpha));
                canvas.drawText("LEVEL " + level + "! 🚀", screenX / 2f, screenY / 2f - 100, paint);
                paint.setAlpha(255);
            }
        }

        private void drawGameOverScreen() {
            // Semi-transparent overlay
            paint.setColor(Color.BLACK);
            paint.setAlpha(180);
            canvas.drawRect(0, 0, screenX, screenY, paint);
            paint.setAlpha(255);

            // Game Over text with funny effect
            paint.setColor(Color.RED);
            paint.setTextSize(82);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("GAME OVER! 💀", screenX / 2f + 2, screenY / 2f - 148, paint);

            paint.setColor(Color.YELLOW);
            paint.setTextSize(80);
            canvas.drawText("GAME OVER! 💀", screenX / 2f, screenY / 2f - 150, paint);

            // Stats
            paint.setColor(Color.WHITE);
            paint.setTextSize(50);
            canvas.drawText("Score: " + score, screenX / 2f, screenY / 2f - 50, paint);
            canvas.drawText("Level: " + level, screenX / 2f, screenY / 2f, paint);
            canvas.drawText("Coins: " + coins_collected + " 💰", screenX / 2f, screenY / 2f + 50, paint);

            paint.setColor(Color.RED);
            canvas.drawText("High Score: " + highScore, screenX / 2f, screenY / 2f + 100, paint);

            // Restart instruction
            paint.setColor(Color.CYAN);
            paint.setTextSize(40);
            canvas.drawText("Tap to restart! 🎮", screenX / 2f, screenY / 2f + 180, paint);
        }

        private void gameOver() {
            gameOver = true;
            if (score > highScore) {
                highScore = score;
            }
            addFunnyEffect("GAME OVER! 😵", screenX/2, screenY/2);
        }

        // Particle and effect methods
        private void addScoreParticles() {
            for (int i = 0; i < 15; i++) {
                int color = Color.HSVToColor(new float[]{random.nextFloat() * 360, 1.0f, 1.0f});
                particles.add(new Particle(
                        bird.x + random.nextInt(40) - 20,
                        bird.y + random.nextInt(40) - 20,
                        random.nextInt(360),
                        color,
                        1200
                ));
            }
        }

        private void addCoinParticles(float x, float y) {
            for (int i = 0; i < 12; i++) {
                particles.add(new Particle(
                        x + random.nextInt(30) - 15,
                        y + random.nextInt(30) - 15,
                        random.nextInt(360),
                        Color.RED,
                        1000
                ));
            }
        }

        private void addFireworks() {
            for (int i = 0; i < 60; i++) {
                int color = Color.HSVToColor(new float[]{random.nextFloat() * 360, 1.0f, 1.0f});
                particles.add(new Particle(
                        screenX / 2f + random.nextInt(300) - 150,
                        screenY / 3f + random.nextInt(150) - 75,
                        random.nextInt(360),
                        color,
                        2500
                ));
            }
        }

        private void addPowerUpParticles(float x, float y) {
            for (int i = 0; i < 20; i++) {
                int color = Color.HSVToColor(new float[]{random.nextFloat() * 360, 1.0f, 1.0f});
                particles.add(new Particle(
                        x + random.nextInt(60) - 30,
                        y + random.nextInt(60) - 30,
                        random.nextInt(360),
                        color,
                        1800
                ));
            }
        }

        private void addFlapParticles() {
            for (int i = 0; i < 8; i++) {
                int color = Color.HSVToColor(new float[]{180 + random.nextFloat() * 60, 0.8f, 1.0f});
                particles.add(new Particle(
                        bird.x - 20,
                        bird.y + 10 + random.nextInt(20) - 10,
                        180 + random.nextInt(60) - 30,
                        color,
                        600
                ));
            }
        }

        private void addFunnyEffect(String text, float x, float y) {
            funnyEffects.add(new FunnyEffect(text, x, y));
        }

        private void addChatBubble(String message) {
            chatBubbles.add(new ChatBubble(message, bird.x + 80, bird.y - 50));
        }

        public void pause() {
            playing = false;
            try {
                gameThread.join();
            } catch (InterruptedException e) {
                // Error
            }
        }

        public void resume() {
            playing = true;
            gameThread = new Thread(this);
            gameThread.start();
        }

        @Override
        public boolean onTouchEvent(MotionEvent motionEvent) {
            switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    if (gameOver) {
                        // Restart game
                        gameOver = false;
                        score = 0;
                        coins_collected = 0;
                        level = 1;
                        currentSpeed = baseSpeed;
                        bird.reset();
                        pipes.clear();
                        particles.clear();
                        powerUps.clear();
                        coins.clear();
                        obstacles.clear();
                        funnyEffects.clear();
                        chatBubbles.clear();
                        flyingEmojis.clear();
                        pipes.add(new Pipe(screenX, screenY, currentSpeed));
                        addFunnyEffect("LET'S GO! 🚀", screenX/2, screenY/2);
                    } else {
                        bird.flap();
                        addFlapParticles();
                        if (random.nextInt(5) == 0) {
                            addChatBubble("Flap! 🪶");
                        }
                    }
                    break;
            }
            return true;
        }
    }

    // Enhanced Bird class
    class Bird {
        float x, y;
        float velY;
        int width = 60, height = 45;
        float gravity = 1.0f;
        float jumpStrength = -15f;
        int screenX, screenY;
        long powerUpTimer = 0;
        boolean hasShield = false;
        boolean hasBoost = false;
        float rotation = 0;
        int currentExpression = 0; // 0=normal, 1=happy, 2=surprised, 3=angry
        long expressionChangeTime = 0;

        public Bird(int screenX, int screenY) {
            this.screenX = screenX;
            this.screenY = screenY;
            reset();
        }

        public void reset() {
            x = screenX / 4f;
            y = screenY / 2f;
            velY = 0;
            powerUpTimer = 0;
            hasShield = false;
            hasBoost = false;
            rotation = 0;
            currentExpression = 0;
        }

        public void update() {
            velY += gravity;
            y += velY;

            // Rotation based on velocity
            rotation = velY * 3f;
            if (rotation > 90) rotation = 90;
            if (rotation < -30) rotation = -30;

            // Update power-up timer
            if (powerUpTimer > 0) {
                powerUpTimer -= 16;
                if (powerUpTimer <= 0) {
                    hasShield = false;
                    hasBoost = false;
                }
            }

            // Change expression occasionally
            if (System.currentTimeMillis() - expressionChangeTime > 2000) {
                expressionChangeTime = System.currentTimeMillis();
                currentExpression = new Random().nextInt(4);
            }
        }

        public void flap() {
            if (hasBoost) {
                velY = jumpStrength * 1.3f;
            } else {
                velY = jumpStrength;
            }
            currentExpression = 1; // Happy expression when flapping
            expressionChangeTime = System.currentTimeMillis();
        }

        public void applyPowerUp(int type) {
            powerUpTimer = 3000; // 3 seconds
            switch (type) {
                case 0: // SHIELD
                    hasShield = true;
                    currentExpression = 2; // Surprised
                    break;
                case 1: // BOOST
                    hasBoost = true;
                    velY = jumpStrength * 1.5f;
                    currentExpression = 1; // Happy
                    break;
                case 2: // SLOW_TIME
                    // Implementation would slow down game time
                    break;
            }
        }

        public void draw(Canvas canvas, Paint paint) {
            canvas.save();
            canvas.rotate(rotation, x + width/2f, y + height/2f);

            // Colorful bird body with gradient effect
            long time = System.currentTimeMillis() / 100;
            float hue = (time * 2) % 360;
            int bodyColor = Color.HSVToColor(new float[]{hue, 0.8f, 1.0f});
            paint.setColor(bodyColor);
            canvas.drawOval(x, y, x + width, y + height, paint);

            // Bird details with complementary colors
            int detailColor = Color.HSVToColor(new float[]{(hue + 60) % 360, 0.9f, 0.9f});
            paint.setColor(detailColor);
            canvas.drawOval(x + 5, y + 5, x + width - 5, y + height - 5, paint);

            // Wing animation with color
            float wingOffset = (float) Math.sin(time * 0.5) * 5;
            int wingColor = Color.HSVToColor(new float[]{(hue + 120) % 360, 0.7f, 0.8f});
            paint.setColor(wingColor);
            canvas.drawOval(x + 10, y + 10 + wingOffset, x + width - 15, y + height - 10 + wingOffset, paint);

            // Eyes based on expression
            drawEyes(canvas, paint);

            // Colorful beak
            paint.setColor(Color.rgb(255, 140, 0));
            canvas.drawCircle(x + width + 7, y + height * 0.5f, 8, paint);

            canvas.restore();

            // Shield effect with rainbow colors
            if (hasShield) {
                long shieldTime = System.currentTimeMillis() / 50;
                for (int i = 0; i < 4; i++) {
                    float shieldHue = (shieldTime * 5 + i * 90) % 360;
                    paint.setColor(Color.HSVToColor(new float[]{shieldHue, 1.0f, 1.0f}));
                    paint.setAlpha(120 - i * 25);
                    canvas.drawCircle(x + width/2f, y + height/2f, width + i * 8, paint);
                }
                paint.setAlpha(255);
            }

            // Boost effect
            if (hasBoost) {
                paint.setColor(Color.YELLOW);
                paint.setAlpha(150);
                canvas.drawOval(x - 10, y - 5, x + width + 10, y + height + 5, paint);
                paint.setAlpha(255);
            }
        }

        private void drawEyes(Canvas canvas, Paint paint) {
            // Left eye
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x + width * 0.6f, y + height * 0.3f, 8, paint);

            // Right eye
            canvas.drawCircle(x + width * 0.8f, y + height * 0.3f, 8, paint);

            // Eye pupils based on expression
            paint.setColor(Color.BLACK);
            switch (currentExpression) {
                case 0: // Normal
                    canvas.drawCircle(x + width * 0.65f, y + height * 0.3f, 4, paint);
                    canvas.drawCircle(x + width * 0.85f, y + height * 0.3f, 4, paint);
                    break;
                case 1: // Happy
                    canvas.drawCircle(x + width * 0.62f, y + height * 0.25f, 3, paint);
                    canvas.drawCircle(x + width * 0.82f, y + height * 0.25f, 3, paint);
                    break;
                case 2: // Surprised
                    canvas.drawCircle(x + width * 0.6f, y + height * 0.3f, 6, paint);
                    canvas.drawCircle(x + width * 0.8f, y + height * 0.3f, 6, paint);
                    break;
                case 3: // Angry
                    canvas.drawCircle(x + width * 0.68f, y + height * 0.35f, 4, paint);
                    canvas.drawCircle(x + width * 0.88f, y + height * 0.35f, 4, paint);
                    break;
            }
        }

        public RectF getBounds() {
            return new RectF(x, y, x + width, y + height);
        }
    }

    // Enhanced Pipe class
    class Pipe {
        float x;
        float topHeight, bottomY;
        int width = 120;
        int gap = 300;
        float speed;
        boolean scored = false;
        int screenX, screenY;
        Random random = new Random();

        public Pipe(int screenX, int screenY, float speed) {
            this.screenX = screenX;
            this.screenY = screenY;
            this.speed = speed;
            x = screenX + width;
            topHeight = random.nextInt(screenY/2) + 50;
            bottomY = topHeight + gap;
        }

        public void update(float newSpeed) {
            this.speed = newSpeed;
            x -= speed;
        }

        public void draw(Canvas canvas, Paint paint) {
            // Colorful pipes with gradient
            long time = System.currentTimeMillis() / 200;
            float hue = (time + x * 0.1f) % 360;
            int pipeColor = Color.HSVToColor(new float[]{hue, 0.6f, 0.8f});

            // Top pipe with gradient
            paint.setColor(pipeColor);
            canvas.drawRect(x, 0, x + width, topHeight, paint);

            // Top pipe cap with brighter color
            int capColor = Color.HSVToColor(new float[]{hue, 0.8f, 1.0f});
            paint.setColor(capColor);
            canvas.drawRect(x - 10, topHeight - 30, x + width + 10, topHeight, paint);

            // Bottom pipe
            paint.setColor(pipeColor);
            canvas.drawRect(x, bottomY, x + width, screenY - 100, paint);

            // Bottom pipe cap
            paint.setColor(capColor);
            canvas.drawRect(x - 10, bottomY, x + width + 10, bottomY + 30, paint);

            // Pipe decorations with complementary colors
            int stripeColor = Color.HSVToColor(new float[]{(hue + 180) % 360, 0.7f, 0.6f});
            paint.setColor(stripeColor);
            for (int i = 0; i < topHeight; i += 40) {
                canvas.drawRect(x, i, x + width, i + 10, paint);
            }
            for (float i = bottomY; i < screenY - 100; i += 40) {
                canvas.drawRect(x, i, x + width, i + 10, paint);
            }
        }

        public boolean collidesWith(Bird bird) {
            if (bird.hasShield) return false;

            RectF birdBounds = bird.getBounds();
            RectF topPipe = new RectF(x, 0, x + width, topHeight);
            RectF bottomPipe = new RectF(x, bottomY, x + width, screenY);

            return birdBounds.intersect(topPipe) || birdBounds.intersect(bottomPipe);
        }
    }

    // Coin class
    class Coin {
        float x, y;
        float speed;
        long animationTime;
        int screenX, screenY;

        public Coin(int screenX, int screenY) {
            this.screenX = screenX;
            this.screenY = screenY;
            x = screenX + 50;
            y = 150 + new Random().nextInt(screenY - 350);
            animationTime = System.currentTimeMillis();
        }

        public void update(float speed) {
            this.speed = speed;
            x -= speed;
            // Floating animation
            y += Math.sin((System.currentTimeMillis() - animationTime) * 0.01) * 2;
        }

        public void draw(Canvas canvas, Paint paint) {
            long time = System.currentTimeMillis() - animationTime;
            float rotation = time * 0.01f;
            float scale = 1.0f + (float) Math.sin(time * 0.008) * 0.2f;

            canvas.save();
            canvas.rotate((float) Math.toDegrees(rotation), x, y);
            canvas.scale(scale, scale, x, y);

            // Gold coin with shine effect
            paint.setColor(Color.rgb(255, 215, 0));
            canvas.drawCircle(x, y, 25, paint);

            paint.setColor(Color.rgb(255, 255, 0));
            canvas.drawCircle(x, y, 20, paint);

            paint.setColor(Color.rgb(255, 215, 0));
            canvas.drawCircle(x, y, 15, paint);

            // Shine effect
            paint.setColor(Color.WHITE);
            paint.setAlpha(150);
            canvas.drawCircle(x - 8, y - 8, 8, paint);
            paint.setAlpha(255);

            canvas.restore();
        }

        public boolean collidesWith(Bird bird) {
            float dx = x - (bird.x + bird.width/2f);
            float dy = y - (bird.y + bird.height/2f);
            float distance = (float) Math.sqrt(dx*dx + dy*dy);
            return distance < 40;
        }
    }

    // Obstacle class
    class Obstacle {
        float x, y;
        float speed;
        int type; // 0=spinning blade, 1=bouncing ball, 2=laser
        long animationTime;
        int screenX, screenY;

        public Obstacle(int screenX, int screenY) {
            this.screenX = screenX;
            this.screenY = screenY;
            x = screenX + 50;
            y = 100 + new Random().nextInt(screenY - 300);
            type = new Random().nextInt(3);
            animationTime = System.currentTimeMillis();
        }

        public void update(float speed) {
            this.speed = speed;
            x -= speed * 0.8f; // Slightly slower than pipes

            // Type-specific movement
            switch (type) {
                case 1: // Bouncing ball
                    y += Math.sin((System.currentTimeMillis() - animationTime) * 0.01) * 3;
                    break;
            }
        }

        public void draw(Canvas canvas, Paint paint) {
            long time = System.currentTimeMillis() - animationTime;

            switch (type) {
                case 0: // Spinning blade
                    canvas.save();
                    canvas.rotate(time * 0.02f, x, y);
                    paint.setColor(Color.RED);
                    for (int i = 0; i < 4; i++) {
                        canvas.rotate(90, x, y);
                        canvas.drawRect(x - 30, y - 5, x + 30, y + 5, paint);
                    }
                    canvas.restore();
                    break;

                case 1: // Bouncing ball
                    float hue = (time * 0.1f) % 360;
                    paint.setColor(Color.HSVToColor(new float[]{hue, 1.0f, 1.0f}));
                    canvas.drawCircle(x, y, 25, paint);
                    paint.setColor(Color.WHITE);
                    canvas.drawCircle(x - 8, y - 8, 8, paint);
                    break;

                case 2: // Laser
                    paint.setColor(Color.RED);
                    paint.setAlpha(200);
                    canvas.drawRect(x - 10, y - 50, x + 10, y + 50, paint);
                    paint.setColor(Color.YELLOW);
                    canvas.drawRect(x - 5, y - 50, x + 5, y + 50, paint);
                    paint.setAlpha(255);
                    break;
            }
        }

        public boolean collidesWith(Bird bird) {
            float dx = x - (bird.x + bird.width/2f);
            float dy = y - (bird.y + bird.height/2f);
            float distance = (float) Math.sqrt(dx*dx + dy*dy);
            return distance < 35;
        }
    }

    // Funny Effect class
    class FunnyEffect {
        String text;
        float x, y;
        float life;
        float maxLife;
        float velY;

        public FunnyEffect(String text, float x, float y) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.life = 2000;
            this.maxLife = 2000;
            this.velY = -2f;
        }

        public void update() {
            y += velY;
            life -= 16;
        }

        public void draw(Canvas canvas, Paint paint) {
            float alpha = life / maxLife;
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(50);
            paint.setTypeface(Typeface.DEFAULT_BOLD);

            // Shadow
            paint.setColor(Color.BLACK);
            paint.setAlpha((int) (100 * alpha));
            canvas.drawText(text, x + 2, y + 2, paint);

            // Main text with rainbow colors
            long time = System.currentTimeMillis() / 100;
            float hue = (time * 10) % 360;
            paint.setColor(Color.HSVToColor(new float[]{hue, 1.0f, 1.0f}));
            paint.setAlpha((int) (255 * alpha));
            canvas.drawText(text, x, y, paint);

            paint.setAlpha(255);
        }
    }

    // Chat Bubble class
    class ChatBubble {
        String message;
        float x, y;
        float life;
        float maxLife;

        public ChatBubble(String message, float x, float y) {
            this.message = message;
            this.x = x;
            this.y = y;
            this.life = 3000;
            this.maxLife = 3000;
        }

        public void update() {
            life -= 16;
            y -= 0.5f; // Float upward
        }

        public void draw(Canvas canvas, Paint paint) {
            float alpha = life / maxLife;

            // Bubble background
            paint.setColor(Color.WHITE);
            paint.setAlpha((int) (200 * alpha));
            canvas.drawRoundRect(x - 50, y - 20, x + 50, y + 20, 20, 20, paint);

            // Bubble border
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setColor(Color.BLACK);
            paint.setAlpha((int) (150 * alpha));
            canvas.drawRoundRect(x - 50, y - 20, x + 50, y + 20, 20, 20, paint);
            paint.setStyle(Paint.Style.FILL);

            // Text
            paint.setColor(Color.BLACK);
            paint.setAlpha((int) (255 * alpha));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(30);
            canvas.drawText(message, x, y + 8, paint);

            paint.setAlpha(255);
        }
    }

    // Flying Emoji class
    class Emoji {
        String emoji;
        float x, y;
        float speed;
        String[] emojis = {"😄", "🎉", "⭐", "🌟", "💫", "🎈", "🦋", "🌈", "☀️", "⚡"};

        public Emoji(int screenX, int screenY) {
            emoji = emojis[new Random().nextInt(emojis.length)];
            x = screenX + 50;
            y = 50 + new Random().nextInt(screenY - 200);
            speed = 2 + new Random().nextFloat() * 3;
        }

        public void update() {
            x -= speed;
            y += Math.sin(System.currentTimeMillis() * 0.01 + x * 0.01) * 2;
        }

        public void draw(Canvas canvas, Paint paint) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(40);
            paint.setColor(Color.WHITE);
            paint.setAlpha(200);
            canvas.drawText(emoji, x + 1, y + 1, paint);
            paint.setAlpha(255);
        }
    }

    // Enhanced Particle class
    class Particle {
        float x, y;
        float velX, velY;
        int color;
        float life;
        float maxLife;
        float size;

        public Particle(float x, float y, float angle, int color, float life) {
            this.x = x;
            this.y = y;
            float speed = 5 + new Random().nextFloat() * 12;
            this.velX = (float) Math.cos(Math.toRadians(angle)) * speed;
            this.velY = (float) Math.sin(Math.toRadians(angle)) * speed;
            this.color = color;
            this.life = life;
            this.maxLife = life;
            this.size = 3 + new Random().nextFloat() * 8;
        }

        public void update() {
            x += velX;
            y += velY;
            velY += 0.3f; // Gravity effect
            velX *= 0.99f; // Air resistance
            life -= 16;
            size *= 0.985f; // Shrink over time
        }

        public void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            float alpha = life / maxLife;
            paint.setAlpha((int) (255 * alpha));
            canvas.drawCircle(x, y, size, paint);
            paint.setAlpha(255);
        }
    }

    // Enhanced PowerUp class
    class PowerUp {
        final int SHIELD = 0;
        final int BOOST = 1;
        final int SLOW_TIME = 2;

        float x, y;
        int type;
        float speed;
        int size = 40;
        long animationTime;

        public PowerUp(int screenX, int screenY) {
            x = screenX + size;
            y = 100 + new Random().nextInt(screenY - 300);
            type = new Random().nextInt(3);
            animationTime = System.currentTimeMillis();
        }

        public void update(float speed) {
            this.speed = speed * 0.9f; // Slightly slower than pipes
            x -= this.speed;
            // Floating animation
            y += Math.sin((System.currentTimeMillis() - animationTime) * 0.006) * 3;
        }

        public void draw(Canvas canvas, Paint paint) {
            float pulse = (float) Math.sin((System.currentTimeMillis() - animationTime) * 0.01) * 0.3f + 1f;
            float drawSize = size * pulse;

            switch (type) {
                case 0: // SHIELD
                    paint.setColor(Color.CYAN);
                    canvas.drawCircle(x, y, drawSize, paint);
                    paint.setColor(Color.WHITE);
                    canvas.drawCircle(x, y, drawSize * 0.7f, paint);
                    paint.setColor(Color.BLUE);
                    canvas.drawCircle(x, y, drawSize * 0.4f, paint);
                    break;
                case 1: // BOOST
                    paint.setColor(Color.RED);
                    canvas.drawCircle(x, y, drawSize, paint);
                    paint.setColor(Color.YELLOW);
                    canvas.drawCircle(x, y, drawSize * 0.6f, paint);
                    paint.setColor(Color.WHITE);
                    canvas.drawCircle(x, y, drawSize * 0.3f, paint);
                    break;
                case 2: // SLOW_TIME
                    paint.setColor(Color.MAGENTA);
                    canvas.drawCircle(x, y, drawSize, paint);
                    paint.setColor(Color.WHITE);
                    canvas.drawCircle(x, y, drawSize * 0.5f, paint);
                    paint.setColor(Color.BLACK);
                    canvas.drawCircle(x, y, drawSize * 0.2f, paint);
                    break;
            }

            // Rainbow sparkle effect
            long sparkleTime = System.currentTimeMillis() / 80;
            for (int i = 0; i < 8; i++) {
                float angle = sparkleTime * 8 + i * 45;
                float sparkleX = x + (float) Math.cos(Math.toRadians(angle)) * drawSize * 1.4f;
                float sparkleY = y + (float) Math.sin(Math.toRadians(angle)) * drawSize * 1.4f;

                float hue = (sparkleTime * 3 + i * 45) % 360;
                int sparkleColor = Color.HSVToColor(new float[]{hue, 1.0f, 1.0f});
                paint.setColor(sparkleColor);
                canvas.drawCircle(sparkleX, sparkleY, 5, paint);
            }
        }

        public boolean collidesWith(Bird bird) {
            float dx = x - (bird.x + bird.width/2f);
            float dy = y - (bird.y + bird.height/2f);
            float distance = (float) Math.sqrt(dx*dx + dy*dy);
            return distance < (size + bird.width/2f);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameView.resume();
    }
}