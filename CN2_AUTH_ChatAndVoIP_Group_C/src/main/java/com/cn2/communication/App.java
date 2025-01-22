package com.cn2.communication;

import java.io.*;
import java.net.*;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;
import java.awt.event.*;
import javax.sound.sampled.*;

public class App extends Frame implements WindowListener, ActionListener {

    // GUI elements
    static TextField inputTextField;        
    static JTextArea textArea;             
    static JFrame frame;                   
    static JButton sendButton;             
    static JTextField meesageTextField;      
    public static Color gray;              
    final static String newline = "\n";    
    static JButton callButton;             

    // Networking variables
    static DatagramSocket socket;         
    static InetAddress peerAddress;        
    static int peerPort = 12345;           
    static int audioPort = 12346;          

    // Audio communication
    static boolean isCalling = false;      
    static TargetDataLine microphone;      
    static SourceDataLine speakers;        

    /**
     * Construct the app's frame and initialize important parameters
     */
    public App(String title) {

        // Setting up the characteristics of the frame
        super(title);                                    
        gray = new Color(254, 254, 254);        
        setBackground(gray);
        setLayout(new FlowLayout());            
        addWindowListener(this);    

        // Setting up the TextField and the TextArea
        inputTextField = new TextField();
        inputTextField.setColumns(20);

        textArea = new JTextArea(10, 40);          
        textArea.setLineWrap(true);               
        textArea.setEditable(false);              
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // Setting up the buttons
        sendButton = new JButton("Send");         
        callButton = new JButton("Call");         
                        
        // Adding the components to the GUI
        add(scrollPane);                                
        add(inputTextField);
        add(sendButton);
        add(callButton);

        // Linking the buttons to the ActionListener
        sendButton.addActionListener(this);            
        callButton.addActionListener(this);    
    }

    /**
     * The main method of the application. It continuously listens for new messages.
     */
 // Networking variables
    static DatagramSocket textSocket;  // Socket for text communication
    static DatagramSocket audioSocket; // Socket for audio communication

    public static void main(String[] args) {
        try {
            // Initialize sockets
            textSocket = new DatagramSocket(peerPort); // Default port for text communication
            audioSocket = new DatagramSocket(audioPort); // Port for audio communication

            peerAddress = InetAddress.getByName("192.168.1.116"); // Change to the actual peer IP if needed
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // Create the app's window
        App app = new App("CN2 - AUTH");
        app.setSize(500, 250);
        app.setVisible(true);

        // Listening loop for receiving text messages
        new Thread(() -> {
            while (true) {
                try {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    // Receive packet on textSocket
                    textSocket.receive(packet);
                    // Convert to String and append to textArea
                    String message = new String(packet.getData(), 0, packet.getLength());
                    textArea.append("Peer: " + message + newline);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
    }

 // Synchronization lock for shared variables
    private static final Object callLock = new Object(); // Volatile ensures thread visibility

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == callButton) {
            synchronized (callLock) {
                if (!isCalling) {
                    // Start a call
                    isCalling = true;
                    callButton.setText("End Call");

                    // Start audio communication threads
                    new Thread(() -> {
                        try {
                            startAudioCommunication();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }).start();
                } else {
                    // End the call
                    isCalling = false;
                    callButton.setText("Call");
                }
            }
        } else if (e.getSource() == sendButton) {
            // The "Send" button was clicked
            try {
                // Get message from text field
                String message = inputTextField.getText();

                // Send message via textSocket
                byte[] buffer = message.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, peerAddress, peerPort);
                textSocket.send(packet);

                // Display the message in the text area
                textArea.append("You: " + message + newline);
                inputTextField.setText("");  // Clear the input field
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public static void startAudioCommunication() throws Exception {
        AudioFormat format = new AudioFormat(8000.0f, 8, 1, true, true);
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
        DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);

        microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
        speakers = (SourceDataLine) AudioSystem.getLine(speakerInfo);

        microphone.open(format);
        speakers.open(format);

        microphone.start();
        speakers.start();

        byte[] buffer = new byte[512];

        // Thread for sending audio
        Thread sender = new Thread(() -> {
            try {
                while (true) {
                    synchronized (callLock) {
                        if (!isCalling) break;
                    }

                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    DatagramPacket packet = new DatagramPacket(buffer, bytesRead, peerAddress, audioPort);
                    audioSocket.send(packet); 
                }
            } catch (Exception ex) {
                // Handle exception and close resources
                System.err.println("Sender thread terminated: " + ex.getMessage());
            } finally {
                microphone.close();
            }
        });

        // Thread for receiving audio
        Thread receiver = new Thread(() -> {
            try {
                while (true) {
                    synchronized (callLock) {
                        if (!isCalling) break;
                    }

                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    audioSocket.receive(packet); 
                    speakers.write(packet.getData(), 0, packet.getLength());
                }
            } catch (Exception ex) {
                // Handle exception and close resources
                System.err.println("Receiver thread terminated: " + ex.getMessage());
            } finally {
                speakers.close();
            }
        });

        sender.start();
        receiver.start();

        // Join threads to ensure clean termination
        sender.join();
        receiver.join();
    }



    // GUI-related window methods
    @Override
    public void windowActivated(WindowEvent e) {}

    @Override
    public void windowClosed(WindowEvent e) {}

    @Override
    public void windowClosing(WindowEvent e) {
        try {
            textSocket.close();
            audioSocket.close();
            if (microphone != null) microphone.close();
            if (speakers != null) speakers.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        dispose();
        System.exit(0);
    }

    @Override
    public void windowDeactivated(WindowEvent e) {}

    @Override
    public void windowDeiconified(WindowEvent e) {}

    @Override
    public void windowIconified(WindowEvent e) {}

    @Override
    public void windowOpened(WindowEvent e) {}
}
