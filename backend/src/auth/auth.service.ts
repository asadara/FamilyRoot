import {
  ConflictException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { JwtService } from '@nestjs/jwt';
import { Repository } from 'typeorm';
import { compare, hash } from 'bcryptjs';
import { UserEntity } from '../users/user.entity';
import { RegisterDto } from './dto/register.dto';

@Injectable()
export class AuthService {
  constructor(
    @InjectRepository(UserEntity)
    private readonly usersRepo: Repository<UserEntity>,
    private readonly jwtService: JwtService,
  ) {}

  async register(dto: RegisterDto) {
    const email = dto.email.trim().toLowerCase();
    const existing = await this.usersRepo.findOne({ where: { email } });
    if (existing) throw new ConflictException('Email is already registered');

    const user = await this.usersRepo.save(
      this.usersRepo.create({
        email,
        phone: null,
        displayName: dto.displayName.trim(),
        passwordHash: await hash(dto.password, 12),
      }),
    );
    return this.issueToken(user);
  }

  async login(emailInput: string, password: string) {
    const email = emailInput.trim().toLowerCase();
    const user = await this.usersRepo
      .createQueryBuilder('user')
      .addSelect('user.passwordHash')
      .where('user.email = :email', { email })
      .getOne();
    if (!user?.passwordHash || !(await compare(password, user.passwordHash))) {
      throw new UnauthorizedException('Email or password is incorrect');
    }
    return this.issueToken(user);
  }

  private async issueToken(user: UserEntity) {
    const accessToken = await this.jwtService.signAsync({
      sub: user.userId,
      email: user.email,
      displayName: user.displayName,
    });
    return {
      accessToken,
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: {
        userId: user.userId,
        email: user.email,
        displayName: user.displayName,
      },
    };
  }
}
