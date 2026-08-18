// Testing CodeGuard webhook
/*CodeGuard comment posted successfully to PR #1
Review saved to MySQL.*/
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
