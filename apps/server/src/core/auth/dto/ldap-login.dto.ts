import { IsNotEmpty, IsString } from 'class-validator';

export class LdapLoginDto {
  @IsNotEmpty()
  @IsString()
  username: string;

  @IsNotEmpty()
  @IsString()
  password: string;
}
