import { useSelector } from 'react-redux';

const SETTING_PUT = 'settings/put';

export const putSettingsAction = (map) => {
  return {
    type: SETTING_PUT,
    payload: map,
  };
};

export const useBooleanSetting = (name) => {
  const value = useSetting(name);
  return value === 'Y' || value === true;
};

export const useSetting = (name) => {
  return useSelector((state) => getSettingFromState(state, name));
};

const getSettingFromState = (state, name) => {
  const value = state?.settings?.backend?.[name];
  console.log('getSettingFromState', { name, value, state });
  return value;
};

export const reducer = (state = {}, action) => {
  const { type, payload } = action;
  switch (type) {
    case SETTING_PUT: {
      return {
        ...state,
        backend: payload,
      };
    }
    default: {
      return state;
    }
  }
};
