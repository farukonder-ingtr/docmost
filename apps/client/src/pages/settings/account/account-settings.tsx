import AccountNameForm from "@/features/user/components/account-name-form";
import ChangeEmail from "@/features/user/components/change-email";
import ChangePassword from "@/features/user/components/change-password";
import AccountAuthSource from "@/features/user/components/account-auth-source";
import { Divider } from "@mantine/core";
import AccountAvatar from "@/features/user/components/account-avatar";
import SettingsTitle from "@/components/settings/settings-title.tsx";
import { useTranslation } from "react-i18next";
import { AccountMfaSection } from "@/features/user/components/account-mfa-section";
import SessionList from "@/features/session/components/session-list";
import { DocumentTitle } from "@/components/ui/document-title.tsx";
import { useAtom } from "jotai";
import { currentUserAtom } from "@/features/user/atoms/current-user-atom.ts";

export default function AccountSettings() {
  const { t } = useTranslation();
  const [currentUser] = useAtom(currentUserAtom);
  const isLdap = currentUser?.user.authSource === "ldap";

  return (
    <>
      <DocumentTitle title={t("My Profile")} />
      <SettingsTitle title={t("My Profile")} />

      <AccountAvatar />

      <AccountNameForm />

      <Divider my="lg" />

      <AccountAuthSource />

      <Divider my="lg" />

      <ChangeEmail />

      {!isLdap && (
        <>
          <Divider my="lg" />

          <ChangePassword />
        </>
      )}

      <Divider my="lg" />

      <AccountMfaSection />

      <Divider my="lg" />

      <SessionList />
    </>
  );
}
