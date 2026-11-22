package bio.anode.ai;

import bio.anode.ai.agent.AgentProperties;
import bio.anode.ai.config.KnowledgeProperties;
import bio.anode.ai.config.LabaiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({LabaiProperties.class, KnowledgeProperties.class, AgentProperties.class})
public class App {

	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}

}
