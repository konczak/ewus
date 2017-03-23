package pl.konczak.nzoz.ewus.domain.checkcwu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import pl.konczak.nzoz.ewus.db.PacjentPagableRepository;
import pl.konczak.nzoz.ewus.db.Patient;
import pl.konczak.nzoz.ewus.domain.authentication.Credentials;
import pl.konczak.nzoz.ewus.domain.authentication.LoginService;
import pl.konczak.nzoz.ewus.domain.authentication.LogoutService;
import pl.konczak.nzoz.ewus.domain.checkcwu.response.CheckCWUResponse;

@Service
public class CheckCWUStatusFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckCWUStatusFacade.class);

    private static final String PESEL_NEW_CHILD = "00000000000";

    private static final String PESEL_INCORRECT_PREFIX = "00000";

    private final LoginService loginService;

    private final CheckCWUStatusService checkCWUStatusService;

    private final LogoutService logoutService;

    private final PacjentPagableRepository pacjentPagableRepository;

    private final CheckCWUForAllReportPersistence checkCWUForAllReportPersistence;

    public CheckCWUStatusFacade(LoginService loginService,
            CheckCWUStatusService checkCWUStatusService,
            LogoutService logoutService,
            PacjentPagableRepository pacjentPagableRepository,
            CheckCWUForAllReportPersistence checkCWUForAllReportPersistence) {
        this.loginService = loginService;
        this.checkCWUStatusService = checkCWUStatusService;
        this.logoutService = logoutService;
        this.pacjentPagableRepository = pacjentPagableRepository;
        this.checkCWUForAllReportPersistence = checkCWUForAllReportPersistence;
    }

    public CheckCWUResponse checkCWU(String pesel) throws Exception {
        LOGGER.debug("start checkCWU proces");

        Credentials credentials = loginService.login();

        CheckCWUResponse checkCWUResponse = checkCWUStatusService.checkCWU(credentials, pesel);

        logoutService.logout(credentials);

        LOGGER.debug("checkCWU proces completed");

        return checkCWUResponse;
    }

    public void checkCWUForAll() throws Exception {
        LOGGER.debug("start checkCWU all proces");

        CheckCWUForAllReport.CheckCWUForAllReportBuilder builder = CheckCWUForAllReport.builder();
        builder.registerStart();

        Credentials credentials = loginService.login();

        final int size = 100;
        int pageNumber = 0;
        Page<Patient> page = pacjentPagableRepository.findPage(pageNumber, size);
        builder.withCountOfAllPesel(page.getTotalElements());

        while (page.hasContent()) {
            page.getContent().stream()
                    .filter(patient -> !patient.getPesel().isEmpty())
                    .filter(patient -> patient.getPesel().length() == 11)
                    .filter(patient -> !PESEL_NEW_CHILD.equals(patient.getPesel()))
                    .filter(patient -> !patient.getPesel().endsWith(PESEL_INCORRECT_PREFIX))
                    .filter(patient -> !patient.isDead())
                    .forEach(patient -> {
                        builder.incrementCountOfCheckedPesel();
                        String pesel = patient.getPesel();
                        try {
                            CheckCWUResponse checkCWUResponse = checkCWUStatusService.checkCWU(credentials, pesel);
                            if (!CheckCWUResponseAnalyzeUtil.isUbezpieczony(checkCWUResponse)) {
                                builder.addPeselBezUbezpieczenia(pesel);
                            }
                        } catch (Exception ex) {
                            builder.addFailedPesel(pesel);
                            LOGGER.error("Failed to checkCWU for <{}> because <{}>", patient.getPesel(), ex.getMessage());
                        }
                    });

            page = pacjentPagableRepository.findPage(++pageNumber, size);
            LOGGER.debug("page <{}> processed", pageNumber);
        }

        pacjentPagableRepository.forceDatabaseStateRefresh();

        logoutService.logout(credentials);

        builder.registerEnd();

        CheckCWUForAllReport checkCWUForAllReport = builder.build();

        checkCWUForAllReportPersistence.persist(checkCWUForAllReport);

        LOGGER.info("finished checkCWU for <{} of {}> found pesel numbers and <{}> has failed, process took <{}>s",
                checkCWUForAllReport.getCountOfCheckedPesel(),
                checkCWUForAllReport.getCountOfAllPesel(),
                checkCWUForAllReport.getCountOfFailedPesels(),
                checkCWUForAllReport.getProcessTimeInSeconds());

        LOGGER.debug("checkCWU all proces completed");
    }
}
