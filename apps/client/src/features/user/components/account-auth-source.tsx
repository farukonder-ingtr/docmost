import { Group, Text } from "@mantine/core";
import { useAtom } from "jotai";
import { currentUserAtom } from "@/features/user/atoms/current-user-atom.ts";
import { useTranslation } from "react-i18next";

export default function AccountAuthSource() {
  const { t } = useTranslation();
  const [currentUser] = useAtom(currentUserAtom);
  const isLdap = currentUser?.user.authSource === "ldap";

  return (
    <Group justify="space-between" wrap="nowrap" gap="xl">
      <div style={{ minWidth: 0, flex: 1 }}>
        <Text size="md">{t("Account type")}</Text>
        <Text size="sm" c="dimmed">
          {isLdap ? t("LDAP") : t("Local")}
        </Text>
      </div>
    </Group>
  );
}
