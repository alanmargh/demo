package com.pe.grum.api.web.rest;

import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codahale.metrics.annotation.Timed;
import com.pe.grum.api.domain.User;
import com.pe.grum.api.repository.UserRepository;
import com.pe.grum.api.security.SecurityUtils;
import com.pe.grum.api.service.MailService;
import com.pe.grum.api.service.UserService;
import com.pe.grum.api.service.dto.UserDTO;
import com.pe.grum.api.web.rest.util.HeaderUtil;
import com.pe.grum.api.web.rest.vm.KeyAndPasswordVM;
import com.pe.grum.api.web.rest.vm.ManagedUserVM;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

/**
 * REST controller for managing the current user's account.
 */
@RestController
@RequestMapping("/api")
public class AccountResource {

    private final Logger log = LoggerFactory.getLogger(AccountResource.class);

    private final UserRepository userRepository;

    private final UserService userService;

    private final MailService mailService;

    private final String ACCOUNT_SID_PROD = "AC8a2bb57df65fd8b2e4e734358bf470d6";
    
    private final String AUTH_TOKEN_PROD = "8c85a170fd11f8defaaf1d475e5141bb";
    
    private final String PHONE_NUMBER_PROD = "+17164076879";
    
    private final String ACCOUNT_SID_DEV = "AC0dc87cb368c24857b17e9b40ced7ba22";
    
    private final String AUTH_TOKEN_DEV = "5b6cc8cbf4320312fac37f5fa38fc524";
    
    private final String PHONE_NUMBER_DEV = "+15005550006";

    public AccountResource(UserRepository userRepository, UserService userService,
            MailService mailService) {

        this.userRepository = userRepository;
        this.userService = userService;
        this.mailService = mailService;
    }

    /**
     * Alanmar 13042017-0103
     * POST  /validation : Método que envía un código de verificación SMS al dispositivo celular
     * @Param id : es el id del usuario que ha realizado el registro
     * @Param phone : es el número de celular de destino
     * @Return ResponseEntity con estatus 200 OK cuando el mensaje a sido enviado con éxito o 400 (Bad Request)
     * con el mensaje de error
     */
    @PostMapping(path="/validation/user/{id}/number/{phone}",
        produces = MediaType.TEXT_PLAIN_VALUE)
    @Timed
    public ResponseEntity sendSmsVerification(@PathVariable Long id, @PathVariable String phone) {

        Twilio.init(ACCOUNT_SID_DEV, AUTH_TOKEN_DEV);
        HttpHeaders textPlainHeaders = new HttpHeaders();
        textPlainHeaders.setContentType(MediaType.TEXT_PLAIN);
        
        String activationKey = userService.getActivationKeyById(id);
        
        try {
        	 Message.creator(
            		new PhoneNumber(phone),
            		new PhoneNumber(PHONE_NUMBER_DEV),
            		activationKey
            		).create();
		} catch (Exception e) {
			return new ResponseEntity<>(e.getMessage(), textPlainHeaders, HttpStatus.BAD_REQUEST);
		}
        return new ResponseEntity<>(HttpStatus.OK); 
    }

    /**
     * Alanmar 13042017-2250
     * se modificó el método para que valide si es un dispositivo móvil o navegador web, si el registro se realiza via web
     * se envía un email de activación de la cuenta, sino se envía el id del nuevo usuario registrado
     * POST  /register : register the user.
     *
     * @param managedUserVM the managed user View Model
     * @return the ResponseEntity with status 201 (Created) if the user is registered or 400 (Bad Request) if the login or e-mail is already in use
     */
    @PostMapping(path = "/register", 
        produces={MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    @Timed
    public ResponseEntity<?> registerAccount(@Valid @RequestBody ManagedUserVM managedUserVM) {

        HttpHeaders textPlainHeaders = new HttpHeaders();
        textPlainHeaders.setContentType(MediaType.TEXT_PLAIN);

        return userRepository.findOneByLogin(managedUserVM.getLogin().toLowerCase())
            .map(user -> new ResponseEntity<>("login already in use", textPlainHeaders, HttpStatus.BAD_REQUEST))
            .orElseGet(() -> userRepository.findOneByEmail(managedUserVM.getEmail())
                .map(user -> new ResponseEntity<>("e-mail address already in use", textPlainHeaders, HttpStatus.BAD_REQUEST))
                .orElseGet(() -> {
                    User user = userService
                        .createUser(managedUserVM.getLogin(), managedUserVM.getPassword(),
                            managedUserVM.getFirstName(), managedUserVM.getLastName(),
                            managedUserVM.getEmail().toLowerCase(), managedUserVM.getImageUrl(),
                            managedUserVM.getLangKey(), managedUserVM.getPhone(), managedUserVM.getMobile());
                    
                    if (managedUserVM.getMobile() == 1) {
                    	mailService.sendActivationEmail(user);
                    	return new ResponseEntity<>(HttpStatus.CREATED);
                    } else {
                    	return new ResponseEntity<>(user.getId().toString(), HttpStatus.OK);
                    }
                })
        );
    }

    /**
     * GET  /activate : activate the registered user.
     *
     * @param key the activation key
     * @return the ResponseEntity with status 200 (OK) and the activated user in body, or status 500 (Internal Server Error) if the user couldn't be activated
     */
    @GetMapping("/activate")
    @Timed
    public ResponseEntity<String> activateAccount(@RequestParam(value = "key") String key) {
        return userService.activateRegistration(key)
            .map(user -> new ResponseEntity<String>(HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /**
     * GET  /authenticate : check if the user is authenticated, and return its login.
     *
     * @param request the HTTP request
     * @return the login if the user is authenticated
     */
    @GetMapping("/authenticate")
    @Timed
    public String isAuthenticated(HttpServletRequest request) {
        log.debug("REST request to check if the current user is authenticated");
        return request.getRemoteUser();
    }

    /**
     * GET  /account : get the current user.
     *
     * @return the ResponseEntity with status 200 (OK) and the current user in body, or status 500 (Internal Server Error) if the user couldn't be returned
     */
    @GetMapping("/account")
    @Timed
    public ResponseEntity<UserDTO> getAccount() {
        return Optional.ofNullable(userService.getUserWithAuthorities())
            .map(user -> new ResponseEntity<>(new UserDTO(user), HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /**
     * POST  /account : update the current user information.
     *
     * @param userDTO the current user information
     * @return the ResponseEntity with status 200 (OK), or status 400 (Bad Request) or 500 (Internal Server Error) if the user couldn't be updated
     */
    @PostMapping("/account")
    @Timed
    public ResponseEntity saveAccount(@Valid @RequestBody UserDTO userDTO) {
        Optional<User> existingUser = userRepository.findOneByEmail(userDTO.getEmail());
        if (existingUser.isPresent() && (!existingUser.get().getLogin().equalsIgnoreCase(userDTO.getLogin()))) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("user-management", "emailexists", "Email already in use")).body(null);
        }
        return userRepository
            .findOneByLogin(SecurityUtils.getCurrentUserLogin())
            .map(u -> {
                userService.updateUser(userDTO.getFirstName(), userDTO.getLastName(), userDTO.getEmail(),
                    userDTO.getLangKey());
                return new ResponseEntity(HttpStatus.OK);
            })
            .orElseGet(() -> new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /**
     * POST  /account/change_password : changes the current user's password
     *
     * @param password the new password
     * @return the ResponseEntity with status 200 (OK), or status 400 (Bad Request) if the new password is not strong enough
     */
    @PostMapping(path = "/account/change_password",
        produces = MediaType.TEXT_PLAIN_VALUE)
    @Timed
    public ResponseEntity changePassword(@RequestBody String password) {
        if (!checkPasswordLength(password)) {
            return new ResponseEntity<>("Incorrect password", HttpStatus.BAD_REQUEST);
        }
        userService.changePassword(password);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * POST   /account/reset_password/init : Send an e-mail to reset the password of the user
     *
     * @param mail the mail of the user
     * @return the ResponseEntity with status 200 (OK) if the e-mail was sent, or status 400 (Bad Request) if the e-mail address is not registered
     */
    @PostMapping(path = "/account/reset_password/init",
        produces = MediaType.TEXT_PLAIN_VALUE)
    @Timed
    public ResponseEntity requestPasswordReset(@RequestBody String mail) {
        return userService.requestPasswordReset(mail)
            .map(user -> {
                mailService.sendPasswordResetMail(user);
                return new ResponseEntity<>("e-mail was sent", HttpStatus.OK);
            }).orElse(new ResponseEntity<>("e-mail address not registered", HttpStatus.BAD_REQUEST));
    }

    /**
     * POST   /account/reset_password/finish : Finish to reset the password of the user
     *
     * @param keyAndPassword the generated key and the new password
     * @return the ResponseEntity with status 200 (OK) if the password has been reset,
     * or status 400 (Bad Request) or 500 (Internal Server Error) if the password could not be reset
     */
    @PostMapping(path = "/account/reset_password/finish",
        produces = MediaType.TEXT_PLAIN_VALUE)
    @Timed
    public ResponseEntity<String> finishPasswordReset(@RequestBody KeyAndPasswordVM keyAndPassword) {
        if (!checkPasswordLength(keyAndPassword.getNewPassword())) {
            return new ResponseEntity<>("Incorrect password", HttpStatus.BAD_REQUEST);
        }
        return userService.completePasswordReset(keyAndPassword.getNewPassword(), keyAndPassword.getKey())
              .map(user -> new ResponseEntity<String>(HttpStatus.OK))
              .orElse(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private boolean checkPasswordLength(String password) {
        return !StringUtils.isEmpty(password) &&
            password.length() >= ManagedUserVM.PASSWORD_MIN_LENGTH &&
            password.length() <= ManagedUserVM.PASSWORD_MAX_LENGTH;
    }
}
