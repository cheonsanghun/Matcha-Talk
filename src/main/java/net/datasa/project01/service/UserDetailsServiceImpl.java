package net.datasa.project01.service; 

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.UserRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with loginId: " + loginId));

        if (!user.isEnabled()) {
            throw new DisabledException("User account is disabled: " + loginId);
        }

        return new org.springframework.security.core.userdetails.User(
                user.getLoginId(),
                user.getPasswordHash(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRolename()))
        );
    }
}
