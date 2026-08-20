import 'reflect-metadata';
import 'dotenv/config';

import { DataSource } from 'typeorm';

import { validateEnvironment } from '../config/environment';
import { createDataSourceOptions } from './typeorm-options';

const environment = validateEnvironment(process.env);

export default new DataSource(createDataSourceOptions(environment));
