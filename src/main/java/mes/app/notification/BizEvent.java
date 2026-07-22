package mes.app.notification;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BizEvent {
    private String domain;
    private String action;
    private Object targetId;
    private String spjangcd;
    private String username;
}
