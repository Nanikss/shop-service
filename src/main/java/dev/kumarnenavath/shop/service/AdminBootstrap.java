package dev.kumarnenavath.shop.service;

import dev.kumarnenavath.shop.ShopProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Signup only ever creates customers, so the first admin comes from configuration. */
@Component
class AdminBootstrap implements ApplicationRunner {

    private final AuthService auth;
    private final ShopProperties props;

    AdminBootstrap(AuthService auth, ShopProperties props) {
        this.auth = auth;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        auth.ensureAdmin(props.admin().email(), props.admin().password());
    }
}
