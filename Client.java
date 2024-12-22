import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.*;

public class Client {
    private static SourceDataLine audioLine;
    private static boolean isPaused = false;
    private static int currentTrackIndex = 0;
    private static List<String> trackList = new ArrayList<>();
    private static List<String> history = new ArrayList<>(); // Liste pour l'historique des morceaux
    private static JLabel currentTrackLabel = new JLabel("No track playing");
    private static boolean isPlaying = false; // Variable d'instance pour savoir si un morceau est en lecture
    private static Thread audioThread;

    public static void main(String[] args) {
        String serverAddress = JOptionPane.showInputDialog(null, "Enter server IP address:");
        int port = Integer.parseInt(JOptionPane.showInputDialog(null, "Enter server port:"));

        fetchTrackList(serverAddress, port);

        // Interface principale pour afficher la liste des pistes
        JFrame frame = new JFrame("Spotify-like Audio Streaming");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLayout(new BorderLayout());

        JLabel title = new JLabel("Available Tracks", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setBorder(new EmptyBorder(10, 10, 10, 10));
        frame.add(title, BorderLayout.NORTH);

        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (String track : trackList) {
            listModel.addElement(track);
        }

        JList<String> trackListView = new JList<>(listModel);
        trackListView.setFont(new Font("SansSerif", Font.PLAIN, 16));
        trackListView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        frame.add(new JScrollPane(trackListView), BorderLayout.CENTER);

        trackListView.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedIndex = trackListView.getSelectedIndex();
                if (selectedIndex >= 0) {
                    currentTrackIndex = selectedIndex;
                    showPlayerUI(serverAddress, port);
                }
            }
        });

        frame.setVisible(true);
    }

    private static void fetchTrackList(String serverAddress, int port) {
        try (Socket socket = new Socket(serverAddress, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             OutputStream out = socket.getOutputStream()) {

            out.write("LIST\n".getBytes());
            out.flush();

            String trackName;
            while ((trackName = reader.readLine()) != null && !trackName.isEmpty()) {
                trackList.add(trackName);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error fetching track list: " + e.getMessage());
        }
    }

    private static void showPlayerUI(String serverAddress, int port) {
        // Créer la fenêtre de lecture
        JFrame frame = new JFrame("Now Playing");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(400, 300);
        frame.setLayout(new BorderLayout());

        // Ajouter le label pour afficher la piste en cours
        currentTrackLabel.setText("Now Playing: " + trackList.get(currentTrackIndex));
        currentTrackLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        currentTrackLabel.setHorizontalAlignment(SwingConstants.CENTER);
        frame.add(currentTrackLabel, BorderLayout.NORTH);

        // Zone de texte pour afficher les logs
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setBackground(new Color(255, 228, 235));
        textArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(textArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Créer les boutons Previous, Pause, Next et History
        JButton previousButton = createStyledButton("Previous");
        JButton pauseButton = createStyledButton("Pause");
        JButton nextButton = createStyledButton("Next");
        JButton historyButton = createStyledButton("History"); // Nouveau bouton pour l'historique

        // Ajouter des actions aux boutons
        previousButton.addActionListener(e -> {
            // Passer à la piste précédente et la jouer
            currentTrackIndex = (currentTrackIndex - 1 + trackList.size()) % trackList.size();
            playAudio(serverAddress, port, textArea);
            isPlaying = true;
            pauseButton.setEnabled(true); // Activer pause
        });

        pauseButton.addActionListener(e -> {
            togglePause(textArea);
        });

        nextButton.addActionListener(e -> {
            // Passer à la piste suivante et la jouer
            currentTrackIndex = (currentTrackIndex + 1) % trackList.size();
            playAudio(serverAddress, port, textArea);
            isPlaying = true;
            pauseButton.setEnabled(true); // Activer pause
        });

        historyButton.addActionListener(e -> {
            // Afficher l'historique dans une nouvelle fenêtre
            showHistory();
        });

        // Ajouter les boutons dans un panneau
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(previousButton);
        buttonPanel.add(pauseButton);
        buttonPanel.add(nextButton);
        buttonPanel.add(historyButton); // Ajouter le bouton Historique
        frame.add(buttonPanel, BorderLayout.SOUTH);

        // Afficher la fenêtre
        frame.setVisible(true);

        // Lancer automatiquement la musique
        playAudio(serverAddress, port, textArea);
    }

    // Fonction pour créer un bouton stylisé
    private static JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setBackground(new Color(255, 182, 193));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        return button;
    }

    private static void playAudio(String serverAddress, int port, JTextArea textArea) {
        try {
            if (audioLine != null && audioLine.isOpen()) {
                audioLine.close();
            }

            Socket socket = new Socket(serverAddress, port);
            OutputStream out = socket.getOutputStream();
            out.write(("PLAY " + currentTrackIndex + "\n").getBytes());
            out.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String trackTitle = reader.readLine();
            currentTrackLabel.setText("Now Playing: " + trackTitle);

            // Ajouter la piste à l'historique
            history.add(trackTitle);

            InputStream serverInput = socket.getInputStream();
            BufferedInputStream bufferedIn = new BufferedInputStream(serverInput);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn);

            AudioFormat baseFormat = audioStream.getFormat();
            AudioFormat decodedFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(),
                16,
                baseFormat.getChannels(),
                baseFormat.getChannels() * 2,
                baseFormat.getSampleRate(),
                false
            );
            AudioInputStream decodedStream = AudioSystem.getAudioInputStream(decodedFormat, audioStream);

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);
            audioLine = (SourceDataLine) AudioSystem.getLine(info);
            audioLine.open(decodedFormat);
            audioLine.start();

            // Créer un tableau tampon de manière "finale"
            final byte[] buffer = new byte[4096];
            final int[] bytesRead = new int[1]; // Utilisation d'un tableau pour la mise à jour de la variable

            textArea.append("Playing track: " + trackTitle + "\n");

            audioThread = new Thread(() -> {
                try {
                    while ((bytesRead[0] = decodedStream.read(buffer)) != -1) {
                        if (isPaused) {
                            synchronized (audioLine) {
                                audioLine.wait();
                            }
                        }
                        audioLine.write(buffer, 0, bytesRead[0]);
                    }

                    audioLine.drain();
                    audioLine.close();
                    socket.close();

                    textArea.append("Playback finished.\n");
                } catch (Exception e) {
                    textArea.append("Error: " + e.getMessage() + "\n");
                }
            });

            audioThread.start();
        } catch (Exception e) {
            textArea.append("Error: " + e.getMessage() + "\n");
        }
    }

    private static void togglePause(JTextArea textArea) {
        if (isPaused) {
            isPaused = false;
            synchronized (audioLine) {
                audioLine.notify();
            }
            textArea.append("Resuming playback...\n");
        } else {
            isPaused = true;
            textArea.append("Playback paused...\n");
        }
    }

    private static void showHistory() {
        // Créer une nouvelle fenêtre pour afficher l'historique
        JFrame historyFrame = new JFrame("History");
        historyFrame.setSize(400, 300);
        historyFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JList<String> historyListView = new JList<>(history.toArray(new String[0]));
        historyListView.setFont(new Font("SansSerif", Font.PLAIN, 16));
        historyFrame.add(new JScrollPane(historyListView), BorderLayout.CENTER);

        historyFrame.setVisible(true);
    }
}
