package link.kongyu.aiBridge;

import com.dtflys.forest.springboot.annotation.ForestScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Luojun
 * @version v1.0.0
 * @since 2026/3/21
 */

@SpringBootApplication
@ForestScan(basePackages = "link.kongyu.napcatqq.client")
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
