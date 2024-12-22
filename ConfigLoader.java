import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

import java.io.File;

public class ConfigLoader {
    public static String loadConfig(String tagName) {
        try {
            // Crée un objet DocumentBuilder
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            // Charge le fichier XML
            File file = new File("config.xml");
            Document doc = builder.parse(file);

            // Normalise le document XML
            doc.getDocumentElement().normalize();

            // Récupère la valeur de l'élément correspondant au tagName
            NodeList nodeList = doc.getElementsByTagName(tagName);
            if (nodeList.getLength() > 0) {
                Node node = nodeList.item(0);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    return element.getTextContent().trim();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
