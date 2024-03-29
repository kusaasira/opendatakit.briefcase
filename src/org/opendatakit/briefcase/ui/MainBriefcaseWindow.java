/*
 * Copyright (C) 2011 University of Washington.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.briefcase.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.FocusTraversalPolicy;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.UIManager;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.opendatakit.aggregate.parser.BaseFormParserForJavaRosa;
import org.opendatakit.briefcase.model.BriefcasePreferences;
import org.opendatakit.briefcase.model.ExportAbortEvent;
import org.opendatakit.briefcase.model.FileSystemException;
import org.opendatakit.briefcase.model.TerminationFuture;
import org.opendatakit.briefcase.model.TransferAbortEvent;
import org.opendatakit.briefcase.util.FileSystemUtils;

public class MainBriefcaseWindow implements WindowListener {
  private static final String BRIEFCASE_VERSION = "ODK Briefcase - " + BriefcasePreferences.VERSION;

  private JFrame frame;
  private JTextField txtBriefcaseDir;
  private JButton btnChoose;
  private PullTransferPanel gatherPanel;
  private PushTransferPanel uploadPanel;
  private ExportPanel exportPanel;
  private final TerminationFuture exportTerminationFuture = new TerminationFuture();
  private final TerminationFuture transferTerminationFuture = new TerminationFuture();

  public static final String AGGREGATE_URL = "aggregate_url";
  public static final String DATE_FORMAT = "yyyy/MM/dd";
  public static final String EXCLUDE_MEDIA_EXPORT = "exclude_media_export";
  public static final String EXPORT_DIRECTORY = "export_directory";
  public static final String EXPORT_END_DATE = "export_end_date";
  public static final String EXPORT_FILENAME = "export_filename";
  public static final String EXPORT_START_DATE = "export_start_date";
  public static final String FORM_ID = "form_id";
  public static final String ODK_PASSWORD = "odk_password";
  public static final String ODK_USERNAME = "odk_username";
  public static final String OVERWRITE_CSV_EXPORT = "overwrite_csv_export";
  public static final String STORAGE_DIRECTORY = "storage_directory";
  public static final String ODK_DIR = "odk_directory";
  public static final String HELP = "help";
  public static final String VERSION = "version";
  public static final String PEM_FILE = "pem_file";

  private static final Log log = LogFactory.getLog(BaseFormParserForJavaRosa.class.getName());

  private JTabbedPane tabbedPane;

  /**
   * Launch the application.
   */
  public static void main(String[] args) {

    if (args.length == 0) {

      EventQueue.invokeLater(new Runnable() {
        public void run() {
          try {
            // Set System L&F
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            MainBriefcaseWindow window = new MainBriefcaseWindow();
            window.frame.setTitle(BRIEFCASE_VERSION);
            ImageIcon icon = new ImageIcon(MainBriefcaseWindow.class.getClassLoader().getResource(
                "odk_logo.png"));
            window.frame.setIconImage(icon.getImage());
            window.frame.setVisible(true);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      });
    } else {
      Options options = addOptions();
      CommandLineParser parser = new BasicParser();
      CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e1) {
            log.error("Launch Failed: " + e1.getMessage());
            showHelp(options);
            System.exit(1);
        }

        if (cmd.hasOption(HELP)) {
            showHelp(options);
            System.exit(1);
        }

        if (cmd.hasOption(VERSION)) {
            showVersion();
            System.exit(1);
        }

        // required for all operations
        if (!cmd.hasOption(FORM_ID) || !cmd.hasOption(STORAGE_DIRECTORY)) {
            log.error(FORM_ID + " and " + STORAGE_DIRECTORY+ " are required");
            showHelp(options);
            System.exit(1);
        }

        // pull from collect or aggregate, not both
        if (cmd.hasOption(ODK_DIR) && cmd.hasOption(AGGREGATE_URL)) {
            log.error("Can only have one of " + ODK_DIR + " or " + AGGREGATE_URL);
            showHelp(options);
            System.exit(1);
        }

        // pull from aggregate
        if (cmd.hasOption(AGGREGATE_URL) && (!(cmd.hasOption(ODK_USERNAME) && cmd.hasOption(ODK_PASSWORD)))) {
            log.error(ODK_USERNAME + " and " + ODK_PASSWORD + " required when " + AGGREGATE_URL + " is specified");
            showHelp(options);
            System.exit(1);
        }

        // export files
        if (cmd.hasOption(EXPORT_DIRECTORY) && !cmd.hasOption(EXPORT_FILENAME) || !cmd.hasOption(EXPORT_DIRECTORY) && cmd.hasOption(EXPORT_FILENAME)) {
            log.error(EXPORT_DIRECTORY + " and " + EXPORT_FILENAME + " are both required to export");
            showHelp(options);
            System.exit(1);
        }

        if (cmd.hasOption(EXPORT_END_DATE)) {
            if (!testDateFormat(cmd.getOptionValue(EXPORT_END_DATE))) {
                log.error("Invalid date format in " + EXPORT_END_DATE);
                showHelp(options);
                System.exit(1);
            }
        }
        if (cmd.hasOption(EXPORT_START_DATE)) {
            if (!testDateFormat(cmd.getOptionValue(EXPORT_START_DATE))) {
                log.error("Invalid date format in " + EXPORT_START_DATE);
                showHelp(options);
                System.exit(1);
            }
        }
      
      
      BriefcaseCLI bcli = new BriefcaseCLI(cmd);
      bcli.run();
    }
  }

  class FolderActionListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      // briefcase...
      establishBriefcaseStorageLocation(true);
    }

  }

  void setFullUIEnabled(boolean state) {
    String path = BriefcasePreferences.getBriefcaseDirectoryIfSet();
    if ( path != null ) {
      txtBriefcaseDir.setText(path + File.separator + FileSystemUtils.BRIEFCASE_DIR);
    } else {
      txtBriefcaseDir.setText("");
    }
    if ( state ) {
      exportPanel.updateComboBox();
      uploadPanel.updateFormStatuses();
      exportPanel.setEnabled(true);
      gatherPanel.setEnabled(true);
      uploadPanel.setEnabled(true);
      tabbedPane.setEnabled(true);
    } else {
      exportPanel.setEnabled(false);
      gatherPanel.setEnabled(false);
      uploadPanel.setEnabled(false);
      tabbedPane.setEnabled(false);
    }
  }

  /**
   * Create the application.
   */
  public MainBriefcaseWindow() {
    frame = new JFrame();
    frame.setBounds(100, 100, 680, 595);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    frame.addWindowListener(new WindowListener() {
      @Override
      public void windowOpened(WindowEvent e) {
      }

      @Override
      public void windowClosing(WindowEvent e) {
      }

      @Override
      public void windowClosed(WindowEvent e) {
      }

      @Override
      public void windowIconified(WindowEvent e) {
      }

      @Override
      public void windowDeiconified(WindowEvent e) {
      }

      @Override
      public void windowActivated(WindowEvent e) {
      }

      @Override
      public void windowDeactivated(WindowEvent e) {
      }
    });

    JLabel lblBriefcaseDirectory = new JLabel(MessageStrings.BRIEFCASE_STORAGE_LOCATION);

    txtBriefcaseDir = new JTextField();
    txtBriefcaseDir.setFocusable(false);
    txtBriefcaseDir.setEditable(false);
    txtBriefcaseDir.setColumns(10);

    btnChoose = new JButton("Change...");
    btnChoose.addActionListener(new FolderActionListener());

    tabbedPane = new JTabbedPane(JTabbedPane.TOP);
    GroupLayout groupLayout = new GroupLayout(frame.getContentPane());
    groupLayout.setHorizontalGroup(groupLayout.createParallelGroup(Alignment.LEADING).addGroup(
        groupLayout
            .createSequentialGroup()
            .addContainerGap()
            .addGroup(
                groupLayout
                    .createParallelGroup(Alignment.LEADING)
                    .addComponent(tabbedPane, Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, 628,
                        Short.MAX_VALUE)
                    .addGroup(
                        groupLayout
                            .createSequentialGroup()
                            .addComponent(lblBriefcaseDirectory)
                            .addGap(18)
                            .addComponent(txtBriefcaseDir, GroupLayout.DEFAULT_SIZE, 362,
                                Short.MAX_VALUE).addGap(18).addComponent(btnChoose)))
            .addContainerGap()));
    groupLayout.setVerticalGroup(groupLayout.createParallelGroup(Alignment.LEADING).addGroup(
        groupLayout
            .createSequentialGroup()
            .addContainerGap()
            .addGroup(
                groupLayout
                    .createParallelGroup(Alignment.BASELINE)
                    .addComponent(txtBriefcaseDir, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnChoose).addComponent(lblBriefcaseDirectory)).addGap(33)
            .addComponent(tabbedPane, GroupLayout.DEFAULT_SIZE, 446, Short.MAX_VALUE)
            .addContainerGap()));

    gatherPanel = new PullTransferPanel(transferTerminationFuture);
    tabbedPane.addTab(PullTransferPanel.TAB_NAME, null, gatherPanel, null);
    PullTransferPanel.TAB_POSITION = 0;

    uploadPanel = new PushTransferPanel(transferTerminationFuture);
    tabbedPane.addTab(PushTransferPanel.TAB_NAME, null, uploadPanel, null);
    PushTransferPanel.TAB_POSITION = 1;

    exportPanel = new ExportPanel(exportTerminationFuture);
    tabbedPane.addTab(ExportPanel.TAB_NAME, null, exportPanel, null);
    frame.getContentPane().setLayout(groupLayout);
    ExportPanel.TAB_POSITION = 2;

    frame.addWindowListener(this);
    setFullUIEnabled(false);

    frame.setFocusTraversalPolicy(new FocusTraversalPolicy() {

      @Override
      public Component getComponentAfter(Container arg0, Component arg1) {
        ArrayList<Component> componentOrdering = new ArrayList<Component>();
        for (;;) {
          int nextPanel = PullTransferPanel.TAB_POSITION;
          componentOrdering.clear();
          componentOrdering.add(txtBriefcaseDir);
          componentOrdering.add(btnChoose);
          componentOrdering.add(tabbedPane);
          int idx = tabbedPane.getSelectedIndex();
          if ( idx == PullTransferPanel.TAB_POSITION ) {
            componentOrdering.addAll(gatherPanel.getTraversalOrdering());
            nextPanel = PushTransferPanel.TAB_POSITION;
          } else if ( idx == PushTransferPanel.TAB_POSITION ) {
            componentOrdering.addAll(uploadPanel.getTraversalOrdering());
            nextPanel = ExportPanel.TAB_POSITION;
          } else if ( idx == ExportPanel.TAB_POSITION ) {
            componentOrdering.addAll(exportPanel.getTraversalOrdering());
            nextPanel = PullTransferPanel.TAB_POSITION;
          }
          componentOrdering.add(btnChoose);
          boolean found = false;
          for ( int i = 0 ; i < componentOrdering.size()-1 ; ++i ) {
            if ( found || arg1 == componentOrdering.get(i) ) {
              found = true;
              Component comp = componentOrdering.get(i+1);
              if ( comp == tabbedPane ) {
                return comp;
              }
              if ( comp.isVisible() && comp.isEnabled() && (!(comp instanceof JTextField) || ((JTextField) comp).isEditable()) ) {
                return comp;
              }
            }
          }
          if ( !found ) {
            return componentOrdering.get(0);
          }
          tabbedPane.setSelectedIndex(nextPanel);
        }
      }

      @Override
      public Component getComponentBefore(Container arg0, Component arg1) {
        ArrayList<Component> componentOrdering = new ArrayList<Component>();
        for (;;) {
          int nextPanel = PullTransferPanel.TAB_POSITION;
          componentOrdering.clear();
          componentOrdering.add(txtBriefcaseDir);
          componentOrdering.add(btnChoose);
          componentOrdering.add(tabbedPane);
          int idx = tabbedPane.getSelectedIndex();
          if ( idx == PullTransferPanel.TAB_POSITION ) {
            componentOrdering.addAll(gatherPanel.getTraversalOrdering());
            nextPanel = ExportPanel.TAB_POSITION;
          } else if ( idx == PushTransferPanel.TAB_POSITION ) {
            componentOrdering.addAll(uploadPanel.getTraversalOrdering());
            nextPanel = PullTransferPanel.TAB_POSITION;
          } else if ( idx == ExportPanel.TAB_POSITION ) {
            componentOrdering.addAll(exportPanel.getTraversalOrdering());
            nextPanel = PushTransferPanel.TAB_POSITION;
          }
          componentOrdering.add(btnChoose);
          boolean found = false;
          for ( int i = componentOrdering.size()-1 ; i > 0 ; --i ) {
            if ( found || arg1 == componentOrdering.get(i) ) {
              found = true;
              Component comp = componentOrdering.get(i-1);
              if ( comp == tabbedPane ) {
                return comp;
              }
              if ( comp.isVisible() && comp.isEnabled() && (!(comp instanceof JTextField) || ((JTextField) comp).isEditable()) ) {
                return comp;
              }
            }
          }
          if ( !found ) {
            return componentOrdering.get(componentOrdering.size()-1);
          }
          tabbedPane.setSelectedIndex(nextPanel);
        }
      }

      @Override
      public Component getDefaultComponent(Container arg0) {
        return btnChoose;
      }

      @Override
      public Component getFirstComponent(Container arg0) {
        return btnChoose;
      }

      @Override
      public Component getLastComponent(Container arg0) {
        return tabbedPane;
      }});
  }

  public void establishBriefcaseStorageLocation(boolean showDialog) {
    // set the enabled/disabled status of the panels based upon validity of default briefcase directory.
    String briefcaseDir = BriefcasePreferences.getBriefcaseDirectoryIfSet();
    boolean reset = false;
    if ( briefcaseDir == null ) {
      reset = true;
    } else {
      File dir = new File(briefcaseDir);
      if ( !dir.exists() || !dir.isDirectory()) {
        reset = true;
      } else {
        File folder = FileSystemUtils.getBriefcaseFolder();
        if ( !folder.exists() || !folder.isDirectory()) {
          reset = true;
        }

      }
    }

    if ( showDialog || reset ) {
      // ask for new briefcase location...
      BriefcaseStorageLocationDialog fs =
          new BriefcaseStorageLocationDialog(MainBriefcaseWindow.this.frame);
      fs.setVisible(true);
      if ( fs.isCancelled() ) {
        // if we need to reset the briefcase location,
        // and have cancelled, then disable the UI.
        // otherwise the value we have is fine.
        setFullUIEnabled(!reset);
      } else {
        String briefcasePath = BriefcasePreferences.getBriefcaseDirectoryIfSet();
        if ( briefcasePath == null ) {
          // we had a bad path -- disable all but Choose...
          setFullUIEnabled(false);
        } else {
          setFullUIEnabled(true);
        }
      }
    } else {
      File f = new File( BriefcasePreferences.getBriefcaseDirectoryProperty());
      if (BriefcaseFolderChooser.testAndMessageBadBriefcaseStorageLocationParentFolder(f, frame)) {
        try {
          FileSystemUtils.assertBriefcaseStorageLocationParentFolder(f);
          setFullUIEnabled(true);
        } catch (FileSystemException e1) {
          e1.printStackTrace();
          ODKOptionPane.showErrorDialog(frame,
              "Unable to create " + FileSystemUtils.BRIEFCASE_DIR,
              "Failed to Create " + FileSystemUtils.BRIEFCASE_DIR);
          // we had a bad path -- disable all but Choose...
          setFullUIEnabled(false);
        }
      } else {
        // we had a bad path -- disable all but Choose...
        setFullUIEnabled(false);
      }
    }
  }

  @Override
  public void windowActivated(WindowEvent arg0) {
    // NO-OP
  }

  @Override
  public void windowClosed(WindowEvent arg0) {
    // NO-OP
  }

  @Override
  public void windowClosing(WindowEvent arg0) {
    exportTerminationFuture.markAsCancelled(new ExportAbortEvent("Main window closed"));
    transferTerminationFuture.markAsCancelled(new TransferAbortEvent("Main window closed"));
  }

  @Override
  public void windowDeactivated(WindowEvent arg0) {
    // NO-OP
  }

  @Override
  public void windowDeiconified(WindowEvent arg0) {
    // NO-OP
  }

  @Override
  public void windowIconified(WindowEvent arg0) {
    // NO-OP
  }

  @Override
  public void windowOpened(WindowEvent arg0) {
    establishBriefcaseStorageLocation(false);
  }

  static void showHelp(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("java -jar briefcase.jar", options);
  }

  static void showVersion() {
    System.out.println("version: "+ BriefcasePreferences.VERSION);
}

  /**
   * Setting up options for Command Line Interface
   * @return
   */
  @SuppressWarnings("static-access")
  static Options addOptions() {
    Options options = new Options();

    Option server = OptionBuilder.withArgName("url").hasArg().withLongOpt(AGGREGATE_URL)
        .withDescription("ODK Aggregate URL (must start with http:// or https://)")
        .create("url");

    Option username = OptionBuilder.withArgName("username").hasArg().withLongOpt(ODK_USERNAME)
        .withDescription("ODK username").create("u");

    Option password = OptionBuilder.withArgName("password").hasArg().withLongOpt(ODK_PASSWORD)
        .withDescription("ODK password").create("p");

    Option formid = OptionBuilder.withArgName("form_id").hasArg().withLongOpt(FORM_ID)
        .withDescription("Form ID of form to download and export").create("id");

    Option storageDir = OptionBuilder
        .withArgName("/path/to/dir")
        .withLongOpt(STORAGE_DIRECTORY)
        .hasArg()
        .withDescription(
            "Directory to create or find ODK Briefcase Storage directory (relative path unless it begins with / or C:\\)")
        .create("sd");

    Option exportDir = OptionBuilder.withArgName("/path/to/dir").hasArg().withLongOpt(EXPORT_DIRECTORY)
        .withDescription("Directory to export the CSV and media files into (relative path unless it begins with / or C:\\)").create("ed");

      Option exportMedia = OptionBuilder.withDescription("Flag to exclude media on export")
        .withLongOpt(EXCLUDE_MEDIA_EXPORT).create("em");

    Option startDate = OptionBuilder.withArgName(DATE_FORMAT).hasArg()
        .withLongOpt(EXPORT_START_DATE).withDescription("Include submission dates after (inclusive) this date in export to CSV")
        .create("start");

    Option endDate = OptionBuilder.withArgName(DATE_FORMAT).hasArg()
        .withLongOpt(EXPORT_END_DATE).withDescription("Include submission dates before (exclusive) this date in export to CSV").create("end");

    Option exportFilename = OptionBuilder.withArgName("name.csv").hasArg()
        .withLongOpt(EXPORT_FILENAME).withDescription("File name for exported CSV")
        .create("f");

    Option overwrite = OptionBuilder.withLongOpt(OVERWRITE_CSV_EXPORT)
        .withDescription("Flag to overwrite CSV on export").create("oc");

    Option help = OptionBuilder.withLongOpt(HELP)
        .withDescription("Print help information (this screen)").create("h");

    Option version = OptionBuilder.withLongOpt(VERSION)
        .withDescription("Print version information").create("v");

    Option odkDir = OptionBuilder.withArgName("/path/to/dir").hasArg().withLongOpt(ODK_DIR)
        .withDescription("/odk directory from ODK Collect (relative path unless it begins with / or C:\\)").create("od");

    Option keyFile = OptionBuilder.withArgName("/path/to/file.pem").hasArg().withLongOpt(PEM_FILE)
        .withDescription("PEM private key file (relative path unless it begins with / or C:\\)").create("pf");

    options.addOption(server);
    options.addOption(username);
    options.addOption(password);
    options.addOption(formid);
    options.addOption(storageDir);
    options.addOption(exportDir);
    options.addOption(exportMedia);
    options.addOption(startDate);
    options.addOption(endDate);
    options.addOption(exportFilename);
    options.addOption(overwrite);
    options.addOption(help);
    options.addOption(version);
    options.addOption(odkDir);
    options.addOption(keyFile);

    return options;
  }
  
  static boolean testDateFormat(String date) {
      try {
          DateFormat df = new SimpleDateFormat(DATE_FORMAT);
          df.parse(date);
      } catch (java.text.ParseException e) {
          return false;     
      }
      return true;         
  }
}
