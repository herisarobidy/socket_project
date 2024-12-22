import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Serveur {
    private static List<String> playlist = new ArrayList<>();
    private static Map<InetAddress, List<String>> historiqueClients = new HashMap<>();
    private static ServerSocket serverSocket;
    private static boolean isRunning = false;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Serveur Audio Streaming");
        frame.setSize(500, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(255, 192, 203));

        JLabel titleLabel = new JLabel("Serveur de Streaming Audio", JLabel.CENTER);
        titleLabel.setFont(new Font("Serif", Font.BOLD, 24));
        titleLabel.setForeground(new Color(255, 105, 180));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        JTextArea textArea = new JTextArea(10, 30);
        textArea.setEditable(false);
        textArea.setBackground(new Color(255, 240, 245));
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(textArea);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(new Color(255, 192, 203));
        JButton stopButton = new JButton("Arrêter le serveur");
        stopButton.setEnabled(false);
        JButton startButton = new JButton("Démarrer le serveur");
        JButton historyButton = new JButton("Afficher l'historique");
        historyButton.setEnabled(false);
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(historyButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setVisible(true);

        startButton.addActionListener(e -> new Thread(() -> startServer(textArea, stopButton, startButton, historyButton)).start());
        stopButton.addActionListener(e -> stopServer(textArea, stopButton, startButton, historyButton));
        historyButton.addActionListener(e -> afficherHistorique(textArea));
    }

    private static void startServer(JTextArea textArea, JButton stopButton, JButton startButton, JButton historyButton) {
        try {
            isRunning = true;
            serverSocket = new ServerSocket(12345);
            textArea.append("Serveur démarré sur le port 12345...\n");

            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            historyButton.setEnabled(true);

            loadPlaylist(textArea);

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                textArea.append("Client connecté : " + clientSocket.getInetAddress() + "\n");
                new Thread(() -> handleClient(clientSocket, textArea)).start();
            }
        } catch (IOException e) {
            textArea.append("Erreur : " + e.getMessage() + "\n");
        } finally {
            stopServer(textArea, stopButton, startButton, historyButton);
        }
    }

    private static void stopServer(JTextArea textArea, JButton stopButton, JButton startButton, JButton historyButton) {
        try {
            isRunning = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            textArea.append("Serveur arrêté.\n");
        } catch (IOException ex) {
            textArea.append("Erreur lors de l'arrêt du serveur : " + ex.getMessage() + "\n");
        } finally {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            historyButton.setEnabled(false);
        }
    }

    private static void loadPlaylist(JTextArea textArea) {
        File folder = new File("audio");
        if (folder.exists() && folder.isDirectory()) {
            for (File file : folder.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".wav")) {
                    playlist.add(file.getAbsolutePath());
                }
            }
            textArea.append("Playlist chargée avec " + playlist.size() + " fichiers audio.\n");
        } else {
            textArea.append("Le dossier 'audio' est introuvable.\n");
        }
    }

    private static void handleClient(Socket clientSocket, JTextArea textArea) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream clientOutput = clientSocket.getOutputStream()
        ) {
            InetAddress clientAddress = clientSocket.getInetAddress();
            historiqueClients.putIfAbsent(clientAddress, new ArrayList<>());

            String request;
            while ((request = reader.readLine()) != null) {
                if (request.equalsIgnoreCase("LIST")) {
                    sendTrackList(clientOutput, textArea);
                } else if (request.startsWith("PLAY")) {
                    int trackIndex = Integer.parseInt(request.split(" ")[1]);
                    if (trackIndex >= 0 && trackIndex < playlist.size()) {
                        String trackPath = playlist.get(trackIndex);
                        String trackTitle = new File(trackPath).getName();
                        historiqueClients.get(clientAddress).add(trackTitle);

                        clientOutput.write((trackTitle + "\n").getBytes());
                        clientOutput.flush();
                        streamAudio(trackPath, clientOutput, textArea);
                    } else {
                        clientOutput.write("INVALID TRACK\n".getBytes());
                    }
                }
            }
        } catch (IOException e) {
            textArea.append("Erreur de communication avec le client : " + e.getMessage() + "\n");
        }
    }

    private static void sendTrackList(OutputStream clientOutput, JTextArea textArea) {
        try {
            for (String track : playlist) {
                String trackName = new File(track).getName();
                clientOutput.write((trackName + "\n").getBytes());
            }
            clientOutput.write("\n".getBytes());
            clientOutput.flush();
        } catch (IOException e) {
            textArea.append("Erreur lors de l'envoi de la liste des pistes : " + e.getMessage() + "\n");
        }
    }

    private static void streamAudio(String trackPath, OutputStream clientOutput, JTextArea textArea) {
        try (InputStream fileInputStream = new FileInputStream(trackPath)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                clientOutput.write(buffer, 0, bytesRead);
            }
            clientOutput.flush();
        } catch (IOException e) {
            textArea.append("Erreur de diffusion : " + e.getMessage() + "\n");
        }
    }

    private static void afficherHistorique(JTextArea textArea) {
        textArea.append("\n=== Historique des écoutes ===\n");
        for (Map.Entry<InetAddress, List<String>> entry : historiqueClients.entrySet()) {
            textArea.append("Client " + entry.getKey() + " :\n");
            for (String track : entry.getValue()) {
                textArea.append("  - " + track + "\n");
            }
        }
        textArea.append("=== Fin de l'historique ===\n");
    }
}
