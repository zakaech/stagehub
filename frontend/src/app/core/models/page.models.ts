/** Miroir de com.stagehub.common.dto.PageResponse. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** Paramètres de pagination envoyés à l'API (page commence à 0, comme Spring Data). */
export interface PageRequest {
  page: number;
  size: number;
  sort?: string;
}
