import { useState } from "react";
import {
  forgotPassword,
  ldapLogin,
  login,
  logout,
  passwordReset,
  setupWorkspace,
  verifyUserToken,
} from "@/features/auth/services/auth-service";
import { useNavigate } from "react-router-dom";
import { useAtom } from "jotai";
import { currentUserAtom } from "@/features/user/atoms/current-user-atom";
import {
  IForgotPassword,
  ILogin,
  IPasswordReset,
  ISetupWorkspace,
  IVerifyUserToken,
} from "@/features/auth/types/auth.types";
import { notifications } from "@mantine/notifications";
import { IAcceptInvite } from "@/features/workspace/types/workspace.types.ts";
import {
  acceptInvitation,
  createWorkspace,
} from "@/features/workspace/services/workspace-service.ts";
import APP_ROUTE, { getPostLoginRedirect } from "@/lib/app-route.ts";
import { RESET } from "jotai/utils";
import { useTranslation } from "react-i18next";
import { isCloud } from "@/lib/config.ts";
import { exchangeTokenRedirectUrl, getHostnameUrl } from "@/ee/utils.ts";

export default function useAuth() {
  const { t } = useTranslation();
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();
  const [, setCurrentUser] = useAtom(currentUserAtom);

  // Local password-store attempt; returns true once handled (success or a
  // redirect like email-verification), false if the caller should fall back.
  const attemptLocalSignIn = async (data: ILogin): Promise<boolean> => {
    try {
      const response = await login(data);

      if (response?.userHasMfa) {
        navigate(APP_ROUTE.AUTH.MFA_CHALLENGE + window.location.search);
      } else if (response?.requiresMfaSetup) {
        navigate(APP_ROUTE.AUTH.MFA_SETUP_REQUIRED + window.location.search);
      } else {
        navigate(getPostLoginRedirect());
      }
      return true;
    } catch (err) {
      const message = err.response?.data?.message;
      if (isCloud() && message?.includes("verify your email")) {
        const sig = err.response?.data?.emailSignature;
        navigate(
          `${APP_ROUTE.AUTH.VERIFY_EMAIL}?email=${encodeURIComponent(data.email)}${sig ? `&sig=${sig}` : ""}`,
        );
        return true;
      }
      return false;
    }
  };

  /**
   * Single login form: tries LDAP first (the entered value doubles as the
   * LDAP username), then falls back to the local password store. Only a
   * generic failure is ever shown — never which of the two attempts failed,
   * to avoid leaking whether an LDAP or local account exists for the input.
   */
  const handleCombinedSignIn = async (data: ILogin) => {
    setIsLoading(true);

    try {
      await ldapLogin({ username: data.email, password: data.password });
      setIsLoading(false);
      navigate(getPostLoginRedirect());
      return;
    } catch {
      // fall through to local password login
    }

    const handled = await attemptLocalSignIn(data);
    setIsLoading(false);

    if (!handled) {
      notifications.show({
        message: t("Login failed"),
        color: "red",
      });
    }
  };

  const handleInvitationSignUp = async (data: IAcceptInvite) => {
    setIsLoading(true);

    try {
      const response = await acceptInvitation(data);
      setIsLoading(false);

      if (response?.requiresLogin) {
        notifications.show({
          message: t(
            "Account created successfully. Please log in to set up two-factor authentication.",
          ),
        });
        navigate(APP_ROUTE.AUTH.LOGIN);
      } else {
        navigate(APP_ROUTE.HOME);
      }
    } catch (err) {
      setIsLoading(false);
      notifications.show({
        message: err.response?.data.message,
        color: "red",
      });
    }
  };

  const handleSetupWorkspace = async (data: ISetupWorkspace) => {
    setIsLoading(true);

    try {
      if (isCloud()) {
        const res = await createWorkspace(data);

        if (res?.requiresEmailVerification) {
          const hostname = res?.workspace?.hostname;
          if (hostname) {
            window.location.href =
              getHostnameUrl(hostname) +
              `/verify-email?email=${encodeURIComponent(data.email)}&sig=${res.emailSignature}`;
          }
          return;
        }

        const hostname = res?.workspace?.hostname;
        const exchangeToken = res?.exchangeToken;
        if (hostname && exchangeToken) {
          window.location.href = exchangeTokenRedirectUrl(
            hostname,
            exchangeToken,
          );
        }
      } else {
        const res = await setupWorkspace(data);
        setIsLoading(false);
        navigate(APP_ROUTE.HOME);
      }
    } catch (err) {
      setIsLoading(false);
      notifications.show({
        message: err.response?.data.message,
        color: "red",
      });
    }
  };

  const handlePasswordReset = async (data: IPasswordReset) => {
    setIsLoading(true);

    try {
      const response = await passwordReset(data);
      setIsLoading(false);

      if (response?.requiresLogin) {
        notifications.show({
          message: t(
            "Password reset was successful. Please log in with your new password.",
          ),
        });
        navigate(APP_ROUTE.AUTH.LOGIN);
      } else {
        navigate(APP_ROUTE.HOME);
        notifications.show({
          message: t("Password reset was successful"),
        });
      }
    } catch (err) {
      setIsLoading(false);
      notifications.show({
        message: err.response?.data.message,
        color: "red",
      });
    }
  };

  const handleLogout = async () => {
    setCurrentUser(RESET);
    await logout();
    window.location.replace(`${APP_ROUTE.AUTH.LOGIN}?logout=1`);
  };

  const handleForgotPassword = async (data: IForgotPassword) => {
    setIsLoading(true);

    try {
      await forgotPassword(data);
      setIsLoading(false);

      return true;
    } catch (err) {
      console.log(err);
      setIsLoading(false);
      notifications.show({
        message: err.response?.data.message,
        color: "red",
      });

      return false;
    }
  };

  const handleVerifyUserToken = async (data: IVerifyUserToken) => {
    setIsLoading(true);

    try {
      await verifyUserToken(data);
      setIsLoading(false);
    } catch (err) {
      console.log(err);
      setIsLoading(false);
      notifications.show({
        message: err.response?.data.message,
        color: "red",
      });
    }
  };

  return {
    signIn: handleCombinedSignIn,
    invitationSignup: handleInvitationSignUp,
    setupWorkspace: handleSetupWorkspace,
    forgotPassword: handleForgotPassword,
    passwordReset: handlePasswordReset,
    verifyUserToken: handleVerifyUserToken,
    logout: handleLogout,
    isLoading,
  };
}
