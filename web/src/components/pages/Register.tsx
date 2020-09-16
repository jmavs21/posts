import React from 'react';
import { Formik, Form } from 'formik';
import { Wrapper } from '../Wrapper';
import { InputField } from '../InputField';
import { Box, Button } from '@chakra-ui/core';
import { register } from '../../services/userService';

interface RegisterProps {}

export interface RegisterValues {
  email: string;
  password: string;
  name: string;
}

export const Register: React.FC<RegisterProps> = () => {
  return (
    <Wrapper variant="small">
      <Formik
        initialValues={{ email: '', password: '', name: '' } as RegisterValues}
        onSubmit={async (values, { setErrors }) => {
          const response = await register(values);
          if (response.errors) {
            setErrors(response.errors);
          } else {
            window.location.href = '/';
          }
        }}
      >
        {({ isSubmitting }) => (
          <Form>
            <InputField name="email" placeholder="email" label="Email" />
            <Box mt={4}>
              <InputField
                name="password"
                placeholder="password"
                label="Password"
                type="password"
              />
            </Box>
            <Box mt={4}>
              <InputField name="name" placeholder="name" label="Name" />
            </Box>
            <Button
              mt={4}
              type="submit"
              isLoading={isSubmitting}
              loadingText="Registering"
              variantColor="blue"
            >
              Register
            </Button>
          </Form>
        )}
      </Formik>
    </Wrapper>
  );
};
