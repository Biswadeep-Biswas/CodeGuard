// Testing CodeGuard lifecycle status 5
public class LoginService {

    public void login(String password) {
        try {
            authenticate(password);
        } catch (Exception e) {
        }
    }

    private void authenticate(String password) {
        System.out.println("USER PASSWORD: " + password);
    }
}
