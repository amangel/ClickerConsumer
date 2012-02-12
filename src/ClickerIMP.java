import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class ClickerIMP implements ConsumerFrame{
	
	//denote what a consumer is expecting answers to be formatted as
	private static final String AVERAGE_FORMATTING = "Avg";
	private static final String COUNT_FORMATTING = "Count";
	private static final String ALL_FORMATTING = "All";
	
	//delimiters
	private static String SEMI_COLON_SEPARATOR = "`/;";
	private static String COMMA_SEPARATOR      = "`/,";
	private static String AMPERSAND_SEPARATOR  = "`/&";
	private static String COLON_SEPARATOR      = "`/:";
	private static String TILDE_SEPARATOR      = "`/~";
	
	private String consumptionString;
	private static String tempString;
	private Map<String, String> currentQuestion;
	
	private Socket socket;
	private BufferedReader reader;
	private PrintWriter writer;
	private JTextField loginIPText;
	private JTextField loginPortText;
	
	//private ArrayList<ClickerConsumerInterface> activeConsumers;
	private String[] acceptedConsumers;
	private static ClickerConsumerInterface consumerInstance;
	private static Class<?> consumerClass;
	private Map<String, Class<?>> availableConsumers; 							//widget identifier, widget class object
	private Map<String, ArrayList<ClickerConsumerInterface>> activeConsumerArray;//group identifier, specific widget instance
	private Map<String, Map<String, Map<String, String>>> allAnswerArray;		//group identifier, <individual identifier, <label, value> > >
	private Map<String, Map<String, Map<String, String>>> countAnswerArray;     //group identifier, <individual identifier, <index, value> > >
	private Map<String, Map<String, Map<String, String>>> averageAnswerArray;   //group identifier, <widget index, <"Average", value> > >
	//private Map<String, ArrayList<String>> answerFormatMap;				//group identifier+questionId, question calculations expected for answers
	
	public ClickerIMP(){
		currentQuestion = Collections.synchronizedMap(new HashMap<String, String>());
		buildGraphics();
	}
	
	private void buildGraphics(){
		
		JFrame frame = new JFrame();
		frame.setSize(400,400);
		JPanel loginPanel = new JPanel();
		loginPanel.setLayout(new GridLayout(3,1));
		JPanel loginIPPanel = new JPanel();
		JPanel loginPortPanel = new JPanel();
		GridLayout oneByTwoGridLayout = new GridLayout(1,2);
		loginIPPanel.setLayout(oneByTwoGridLayout);
		loginPortPanel.setLayout(oneByTwoGridLayout);
		
		JLabel loginIPLabel = new JLabel("Login IP: ");
		JLabel loginPortLabel = new JLabel("Login port: ");
		loginIPText = new JTextField();
		loginPortText = new JTextField();
		
		loginIPText.setText("134.161.42.14");
		loginPortText.setText("7171");
		
		loginIPPanel.add(loginIPLabel);
		loginIPPanel.add(loginIPText);
		
		loginPortPanel.add(loginPortLabel);
		loginPortPanel.add(loginPortText);
		
		JButton loginButton = new JButton("Login");
		loginButton.addActionListener(new ActionListener(){

			@Override
			public void actionPerformed(ActionEvent arg0) {
				doLogin();
			}});
		
		JButton loadConsumersButton = new JButton("Load consumers");
		loadConsumersButton.addActionListener(new ActionListener(){

			@Override
			public void actionPerformed(ActionEvent e) {
				loadConsumersFromSubdirectory();
			}});
			
		loginPanel.add(loginIPPanel);
		loginPanel.add(loginPortPanel);
		loginPanel.add(loginButton);
		//loginPanel.add(loadConsumersButton);
		
		//loadConsumersFromSubdirectory();
		
		frame.add(loginPanel);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
		

	}
	
	private String buildConsumptionNotificationString(){
		//Each consumption will be a Title,expected value:type;expected value:type
		//no ev:t will imply they take everything
		String tempConsumptionString = "";
		Iterator<String> i = availableConsumers.keySet().iterator();
		while(i.hasNext()){
			tempConsumptionString += i.next()+COMMA_SEPARATOR;
		}
		return tempConsumptionString.substring(0, tempConsumptionString.length()-3);
	}
	
	private void doLogin(){
		try {
			loadConsumersFromSubdirectory();
			socket = new Socket(loginIPText.getText(), Integer.parseInt(loginPortText.getText()));
			socket.setKeepAlive(true);
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			writer = new PrintWriter(socket.getOutputStream(), true);
			writer.println("frederis");
			
			writer.flush();
			writer.println(getConsumptionString());
			writer.flush();
			Thread thread = new Thread(new HandlingRunnable());
			thread.start();
			System.out.println("Login successful.");
			
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.out.println("Login unsuccessful.");
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Login unsuccessful.");
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Login unsuccessful.");
		}
	}
	
	private void loadConsumersFromSubdirectory(){
		availableConsumers = Collections.synchronizedMap(new HashMap<String, Class<?>>());
		activeConsumerArray = Collections.synchronizedMap(new HashMap<String, ArrayList<ClickerConsumerInterface>>());
		allAnswerArray = Collections.synchronizedMap(new HashMap<String, Map<String, Map<String, String>>>() );
		countAnswerArray = Collections.synchronizedMap(new HashMap<String, Map<String, Map<String, String>>>() );
		averageAnswerArray = Collections.synchronizedMap(new HashMap<String, Map<String, Map<String, String>>>() );
		File consumerDirectory = new File("./consumers/");
		String[] files = consumerDirectory.list();
		URLClassLoader urlcl = null;
		try {
			urlcl = new URLClassLoader(new URL[]{(consumerDirectory.toURI().toURL())});
		} catch (MalformedURLException e1) {
			e1.printStackTrace();
		}
		//ClickerConsumerInterface cca = new ClickerConsumerAdapter();
		for(String s: files){
			try {
				if(s.length() < 6){
					System.out.println("Filename: "+ s + "is too short to be an appropriate java class file. Skipping.");
					continue;
				}
				consumerClass = urlcl.loadClass(s.substring(0,s.length()-6));
				boolean works = ClickerConsumerInterface.class.isAssignableFrom(consumerClass);
				if(works){
					consumerInstance = (ClickerConsumerInterface)consumerClass.newInstance();
					consumerInstance.setParent(this);
					String temporaryConsumption = consumerInstance.declareConsumptions();
					String[] temporaryConsumptionArray = temporaryConsumption.split(",");
					availableConsumers.put(temporaryConsumptionArray[0], consumerClass);
				} else {
					System.out.println(s + " does not properly fit the necessary interface. Skipping.");
				}
			} catch (ClassNotFoundException e) {
				System.out.println(s + " was not an appropriately formed java class file. Skipping.");
				continue;
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (NoClassDefFoundError e) {
				System.out.println("Invalid class file " + s + " found. Skipping.");
				continue;
			}
		}
	}
	
	private String getConsumptionString(){
		String tempConsumptionString = "IConsume"+SEMI_COLON_SEPARATOR;
		tempConsumptionString += buildConsumptionNotificationString();
		return tempConsumptionString;
	}
	
	private void getActivePlugins(String[] consumerArray, String groupName, String question){
		for (String s : consumerArray){
			String[] pluginInformation = s.split(COMMA_SEPARATOR);
			if(availableConsumers.containsKey(s)){
				ClickerConsumerInterface cci = null;
				try {
					cci = (ClickerConsumerInterface) availableConsumers.get(pluginInformation[0]).newInstance();
					cci.setID(groupName);
					cci.setQuestion(question);
					cci.setActiveStatus(true);
				} catch (InstantiationException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
				if(activeConsumerArray.containsKey(groupName)){
					activeConsumerArray.get(groupName).add(cci);
				} else {
					ArrayList<ClickerConsumerInterface> cciArray = new ArrayList<ClickerConsumerInterface>();
					cciArray.add(cci);
					activeConsumerArray.put(groupName, cciArray);
				}
			}
		}
	}
	
	private void distributeValues(String group, Map<String, Map<String, String>> answerMapToDistribute){
												  //  Person name, index#, value
		//Map<String, ArrayList<ClickerConsumerInterface>> activeConsumerArray;//group identifier, specific widget instance
		System.out.println("output: "+answerMapToDistribute);
		if(activeConsumerArray.containsKey(group)){
			ArrayList<ClickerConsumerInterface> activeConsumers =activeConsumerArray.get(group);
			for(ClickerConsumerInterface cc : activeConsumers){
				Thread thread = new Thread(new MessagePassingRunnable(cc, answerMapToDistribute));
				thread.start();
			}
		}
	}
	
	private Map<String, String> calculateAll(String group, String input){
		String[] splitAnswers = input.split(COMMA_SEPARATOR);
		Map<String, String> answersToReturn = Collections.synchronizedMap(new HashMap<String, String>());
		for (int i = 0; i<splitAnswers.length; i++){
			answersToReturn.put(""+i, splitAnswers[i]);
		}
		return answersToReturn;
	}
	
	private void processNewInput(String clientName, String clientGroup, String values){
		//answerArray;	//group identifier, <individual identifier, <label, value> > >
		Map<String, Map<String, String>> groupAnswerMap = null;//map for a specific group
		if(!allAnswerArray.containsKey(clientGroup)){
			groupAnswerMap = Collections.synchronizedMap(new HashMap<String, Map<String, String>>());
		} else {
			groupAnswerMap = allAnswerArray.get(clientGroup);
		}
		Map<String, String> individualCurrentAnswers = null;//map of answers for a specific individual
		if(!groupAnswerMap.containsKey(clientName)){
			individualCurrentAnswers = Collections.synchronizedMap(new HashMap<String, String>());
		} else {
			individualCurrentAnswers = groupAnswerMap.get(clientName);
		}
		individualCurrentAnswers = calculateAll(clientGroup, values);
		groupAnswerMap.put(clientName, individualCurrentAnswers);
		//System.out.println("individualCurrentAnswers: "+individualCurrentAnswers);
		for (ClickerConsumerInterface cci : activeConsumerArray.get(clientGroup)){
			String[] dec = cci.declareConsumptions().split(COLON_SEPARATOR);
			if(dec[1].equalsIgnoreCase("All")){
				Map<String, Map<String, String>> distributableAnswerAll = new HashMap<String, Map<String, String>>();
				distributableAnswerAll.put(clientName, individualCurrentAnswers);
				distributeValues(clientGroup, distributableAnswerAll);
				
				//TODO: iterate through, add in a No answer category if someone hasn't submitted an answer yet
			} else if (dec[1].equalsIgnoreCase("Count")){
				Iterator iteratePerson = groupAnswerMap.keySet().iterator();//  Person name, index#, value
				String iteratePersonNext = "";
				Map<String, String> totals = new HashMap<String, String>();
				//int totalCount = 0;
				String[] questionParts = currentQuestion.get(clientGroup).split(SEMI_COLON_SEPARATOR);
				String[] widgets = questionParts[3].split(COMMA_SEPARATOR);
				for (String widget : widgets){
					String[] widgetParts = widget.split(COLON_SEPARATOR);
					if(widgetParts[0].equals("B") || widgetParts[0].equals("TOG")){
						totals.put(widgetParts[1], "0");
					} else if (widgetParts[0].equals("COMBO")){
						//widgetParts[2] = options
						String[] comboOptions = widgetParts[2].split(TILDE_SEPARATOR);
						for (String s : comboOptions){
							totals.put(s, "0");
						}
					}
				}
				while(iteratePerson.hasNext()){
					iteratePersonNext = (String) iteratePerson.next();
					Iterator iterateIndex = groupAnswerMap.get(iteratePersonNext).keySet().iterator();
					String iterateIndexNext = "";
					while (iterateIndex.hasNext()){
						iterateIndexNext = (String) iterateIndex.next();
						String nextValue = groupAnswerMap.get(iteratePersonNext).get(iterateIndexNext);
						if(!nextValue.equals(" ")){
							if(totals.containsKey(nextValue)){
								totals.put(nextValue, ""+(Integer.parseInt(totals.get(nextValue)) + 1));
							} else {
								totals.put(nextValue, "1");
							}
						}
					}
				}
				Map<String, Map<String, String>> answersToInsert = new HashMap<String, Map<String, String>>();
				answersToInsert.put("Count", totals);
				countAnswerArray.put(clientGroup, answersToInsert);
				distributeValues(clientGroup, answersToInsert);
			} else if (dec[1].equalsIgnoreCase("Avg")){
				int widgetCount = Integer.parseInt(dec[2]);
				//individualCurrentAnswers      single persons      index : value
				//groupAnswerMap			person     :     index   : value
				Map<String, Integer> averagePersonCounts = new HashMap<String, Integer>();
				Iterator iteratePerson = groupAnswerMap.keySet().iterator();
				String personNext = "";
				Map<String, Integer> totalsToAverage = new HashMap<String, Integer>();
				Map<String, String> person = null;
				while (iteratePerson.hasNext()){
					personNext = (String) iteratePerson.next();
					person = groupAnswerMap.get(personNext);
					Iterator indexIterator = person.keySet().iterator();
					String indexNext = "";
					while(indexIterator.hasNext()){
						indexNext = (String) indexIterator.next();
						Integer value = 0;
						try{
							value = Integer.parseInt(person.get(indexNext));
							if(averagePersonCounts.containsKey(indexNext)){
								averagePersonCounts.put(indexNext, averagePersonCounts.get(indexNext) + 1);
							} else {
								averagePersonCounts.put(indexNext, 1);
							}
						} catch (Exception e) {
							System.out.println("integer.parseint exception");
							e.printStackTrace();
						}
						if(totalsToAverage.containsKey(indexNext)){
							totalsToAverage.put(indexNext, totalsToAverage.get(indexNext) + value);
						} else {
							totalsToAverage.put(indexNext, value);
						}
					}
				}
				Map<String, Map<String, String>> answersToInsert = new HashMap<String, Map<String, String>>();
				Iterator totalsIterator = totalsToAverage.keySet().iterator();
				String index = "";
				while(totalsIterator.hasNext()){
					index = (String) totalsIterator.next();
					float averageValue = (float)totalsToAverage.get(index) / (float)averagePersonCounts.get(index) ; 
					Map<String, String> answerMapToInsert = new HashMap<String, String>();
					answerMapToInsert.put("Average", ""+averageValue);
					answersToInsert.put(index, answerMapToInsert );
				}
				averageAnswerArray.put(clientGroup, answersToInsert);
				distributeValues(clientGroup, answersToInsert);
				//split dec, see if the beginning is average, if it is, then get the count
				//should be Avg,#
			} else {
				System.out.println("Error, unspecified means of answer distribution to "+cci.declareConsumptions());
			}
		}
	}
	
	private class HandlingRunnable implements Runnable{
		private String str;
		private String[] strParts;
		private boolean run;
		private String[] emptyStringArray = {""};
		
		public void run(){
			run = true;
			while(run){
				try {
					str = reader.readLine();
					strParts = str.split(SEMI_COLON_SEPARATOR);
					if(strParts[0].equalsIgnoreCase("Open") || strParts[0].equalsIgnoreCase("OpenClickPad")){
	//expecting: Open`/;ID`;/Widgets`/&pluginName`/:typeField1`/,index`/,index`/:typeField2`/,index`/;pluginName2`/:typeField1`/,index`/&groupName`/,groupName2`/:#
						System.out.println("Question is: "+str);
						String[] temp = str.split(AMPERSAND_SEPARATOR); //question&plugin&group
						//currentQuestion = str;
						try{
							acceptedConsumers = temp[1].split(COMMA_SEPARATOR);
						} catch (ArrayIndexOutOfBoundsException e){
							acceptedConsumers = emptyStringArray;
						}
						for(String groupName : temp[2].split(COMMA_SEPARATOR)){
							String[] groupNameParts = groupName.split(COLON_SEPARATOR);
							currentQuestion.put(groupNameParts[0], str);
							getActivePlugins(acceptedConsumers, groupNameParts[0], currentQuestion.get(groupNameParts[0]));
							allAnswerArray.put(groupNameParts[0], Collections.synchronizedMap(new HashMap<String, Map<String, String>>() ));
						}
					} else if(strParts[0].equals("Close")){
						for(String groupName : strParts[1].split(COMMA_SEPARATOR)){
							removePlugins(groupName);
							removeAnswers(groupName);
						}
					} else if(str.length()>0){
						System.out.println("Received: "+str);//TODO: remove debug printouts
						if(activeConsumerArray.containsKey(strParts[1])){
							processNewInput(strParts[0], strParts[1], strParts[3]);
						}
					}
				} catch(SocketException e){
					run = false;
				} catch(Exception e){
					run = false;
					e.printStackTrace();
				}
			}
		}
	}

	private class MessagePassingRunnable implements Runnable {
		private ClickerConsumerInterface consumer;
		private Map<String, Map<String, String>> inputMap;
		public MessagePassingRunnable(ClickerConsumerInterface cci, Map<String, Map<String, String>> input){
			consumer = cci;
			inputMap = input;
		}
		
		@Override
		public void run() {
			consumer.inputData(inputMap);
		}
	}

	@Override
	public String getData() {
		return null;//currentQuestion;
	}

	private void removePlugins(String string) {
		System.out.println("Close called on: "+string);
		ArrayList<ClickerConsumerInterface> cciArray = activeConsumerArray.get(string);
		for(ClickerConsumerInterface cci : cciArray){
			cci.setActiveStatus(false);
		}
		activeConsumerArray.put(string, null);
		activeConsumerArray.remove(string);	
	}
	
	private void removeAnswers(String groupName){
		allAnswerArray.put(groupName, null);
		allAnswerArray.remove(groupName);
		
	}
	
}
