// Testing CodeGuard webhook
public class LoginService {

    public void login(String password) {
        try {
            authenticate(password);
        } catch (Exception e) {
        }
    }

    private void authenticate(String password) {
        System.out.println(password);
    }
}
