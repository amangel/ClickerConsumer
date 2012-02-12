import java.util.Map;

public interface ClickerConsumerInterface {
	public void setParent(ConsumerFrame parent);
	public void setID(String id);
	public String declareConsumptions();
	public void setActiveStatus(boolean status);
	public boolean getActiveStatus();
	public void inputData(Map<String, Map<String, String>> input);//  Person name, index#, value
	public void setQuestion(String question);
}
