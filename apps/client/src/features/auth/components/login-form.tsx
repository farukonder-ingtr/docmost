import { z } from "zod/v4";
import { useForm } from "@mantine/form";
import { zod4Resolver } from "mantine-form-zod-resolver";
import useAuth from "@/features/auth/hooks/use-auth";
import {
  Container,
  Title,
  TextInput,
  Button,
  PasswordInput,
  Box,
  Anchor,
  Group,
} from "@mantine/core";
import classes from "./auth.module.css";
import { useRedirectIfAuthenticated } from "@/features/auth/hooks/use-redirect-if-authenticated.ts";
import { Link } from "react-router-dom";
import APP_ROUTE from "@/lib/app-route.ts";
import { useTranslation } from "react-i18next";
import SsoLogin from "@/ee/components/sso-login.tsx";
import { useWorkspacePublicDataQuery } from "@/features/workspace/queries/workspace-query.ts";
import { Error404 } from "@/components/ui/error-404.tsx";
import React from "react";
import { AuthLayout } from "./auth-layout.tsx";

// Same field doubles as Docmost email (password sign-in) or LDAP username (company sign-in).
const formSchema = z.object({
  email: z.string().min(1, { message: "Email or username is required" }),
  password: z.string().min(1, { message: "Password is required" }),
});
type FormValues = z.infer<typeof formSchema>;

export function LoginForm() {
  const { t } = useTranslation();
  const { signIn, ldapSignIn, isLoading } = useAuth();
  useRedirectIfAuthenticated();
  const {
    data,
    isLoading: isDataLoading,
    isError,
    error,
  } = useWorkspacePublicDataQuery();

  const form = useForm<FormValues>({
    validate: zod4Resolver(formSchema),
    initialValues: {
      email: "",
      password: "",
    },
  });

  function handleValidationFailure(errors: Record<string, unknown>) {
    const firstInvalidId = Object.keys(errors)[0];
    if (firstInvalidId) {
      document.getElementById(firstInvalidId)?.focus();
    }
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const { hasErrors, errors } = form.validate();
    if (hasErrors) {
      handleValidationFailure(errors);
      return;
    }

    // Which of the two submit buttons was pressed decides email+password vs. LDAP username+password.
    const submitter = (event.nativeEvent as SubmitEvent)
      .submitter as HTMLButtonElement | null;

    if (submitter?.value === "ldap") {
      await ldapSignIn({
        username: form.values.email,
        password: form.values.password,
      });
      return;
    }

    const emailCheck = z.email().safeParse(form.values.email);
    if (!emailCheck.success) {
      form.setFieldError("email", t("Enter a valid email"));
      document.getElementById("email")?.focus();
      return;
    }

    await signIn({ email: form.values.email, password: form.values.password });
  }

  if (isDataLoading) {
   return null;
  }

  if (isError && error?.["response"]?.status === 404) {
    return <Error404 />;
  }

  return (
    <AuthLayout>
      <Container size={420} className={classes.container}>
        <Box p="xl" className={classes.containerBox}>
          <Title order={1} size="h2" ta="center" fw={500} mb="md">
            {t("Login")}
          </Title>

          <SsoLogin />

          {!data?.enforceSso && (
            <form onSubmit={handleSubmit}>
              <TextInput
                id="email"
                label={t("Email or username")}
                placeholder="email@example.com"
                variant="filled"
                autoComplete="username"
                errorProps={{ role: "alert" }}
                {...form.getInputProps("email")}
              />

              <PasswordInput
                id="password"
                label={t("Password")}
                placeholder={t("Your password")}
                variant="filled"
                mt="md"
                autoComplete="current-password"
                errorProps={{ role: "alert" }}
                visibilityToggleButtonProps={{
                  "aria-label": t("Toggle password visibility"),
                  "aria-hidden": false,
                  tabIndex: 0,
                }}
                {...form.getInputProps("password")}
              />

              <Group justify="flex-end" mt="sm">
                <Anchor
                  to={APP_ROUTE.AUTH.FORGOT_PASSWORD}
                  component={Link}
                  underline="never"
                  size="sm"
                >
                  {t("Forgot your password?")}
                </Anchor>
              </Group>

              <Button
                type="submit"
                name="intent"
                value="password"
                fullWidth
                mt="md"
                loading={isLoading}
              >
                {t("Sign In")}
              </Button>

              <Button
                type="submit"
                name="intent"
                value="ldap"
                variant="outline"
                fullWidth
                mt="sm"
                loading={isLoading}
              >
                {t("Sign in with company account (LDAP)")}
              </Button>
            </form>
          )}
        </Box>
      </Container>
    </AuthLayout>
  );
}
